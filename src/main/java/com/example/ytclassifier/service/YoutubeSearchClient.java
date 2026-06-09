package com.example.ytclassifier.service;

import com.example.ytclassifier.service.dto.YoutubeChannelResponse;
import com.example.ytclassifier.service.dto.YoutubePlaylistItemsResponse;
import com.example.ytclassifier.service.dto.YoutubeSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * YouTube search.list 호출(1회 = 1페이지).
 *
 * <p>중요: q의 파이프('|')는 OR 연산자이며 %7C로 '정확히 한 번만' 인코딩되어야 한다.
 * RestClient 기본 UriBuilder(DefaultUriBuilderFactory, TEMPLATE_AND_VALUES)는 queryParam 값으로
 * 전달된 원문 '|'를 한 번만 인코딩한다. 절대 미리 인코딩(%7C)하거나 템플릿에 직접 이어붙이지 말 것.
 *
 * <p>일시적 호출 제한(429 / 403 rateLimitExceeded)은 지수 백오프로 자동 재시도한다.
 * 쿼터 소진(403 quotaExceeded)이나 그 외 오류는 재시도하지 않고 그대로 던진다(상위에서 분류·기록).
 */
@Component
public class YoutubeSearchClient {

    private static final int MAX_RETRIES = 3;
    private static final Logger log = LoggerFactory.getLogger(YoutubeSearchClient.class);

    private final RestClient youtubeRestClient;
    /** 재시도 백오프 기준(ms). 1회차 base, 2회차 2*base, 3회차 4*base. 테스트에서 0으로 낮춘다. */
    private long retryBaseMillis = 1000L;

    public YoutubeSearchClient(RestClient youtubeRestClient) {
        this.youtubeRestClient = youtubeRestClient;
    }

    /** 테스트 전용: 재시도 백오프를 빠르게(0ms) 만든다. */
    void setRetryBaseMillis(long retryBaseMillis) {
        this.retryBaseMillis = retryBaseMillis;
    }

    /**
     * @param q               "k1|k2|k3" 형태(원문 파이프 유지)
     * @param maxResults      1~50
     * @param order           date/relevance/...
     * @param apiKey          API Key
     * @param publishedAfter  RFC3339 또는 null
     * @param publishedBefore RFC3339 또는 null
     * @param pageToken       다음 페이지 토큰 또는 null
     * @param videoDuration   short/medium/long, 또는 null/"any"(필터 없음)
     */
    public YoutubeSearchResponse search(String q, int maxResults, String order, String apiKey,
                                        String publishedAfter, String publishedBefore, String pageToken,
                                        String videoDuration) {
        return withRetries("search.list", q, () ->
                doSearch(q, maxResults, order, apiKey, publishedAfter, publishedBefore, pageToken, videoDuration));
    }

    public YoutubeChannelResponse channelById(String channelId, String apiKey) {
        return withRetries("channels.list", channelId, () ->
                doChannel("id", channelId, apiKey));
    }

    public YoutubeChannelResponse channelByHandle(String handle, String apiKey) {
        return withRetries("channels.list", handle, () ->
                doChannel("forHandle", handle, apiKey));
    }

    public YoutubeChannelResponse channelByUsername(String username, String apiKey) {
        return withRetries("channels.list", username, () ->
                doChannel("forUsername", username, apiKey));
    }

    public YoutubePlaylistItemsResponse playlistItems(String playlistId, int maxResults, String apiKey, String pageToken) {
        return withRetries("playlistItems.list", playlistId, () ->
                doPlaylistItems(playlistId, maxResults, apiKey, pageToken));
    }

    private <T> T withRetries(String operation, String target, Supplier<T> request) {
        int attempt = 0;
        while (true) {
            try {
                return request.get();
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                String body = ex.getResponseBodyAsString();
                if (YoutubeApiErrors.isRateLimit(status, body) && attempt < MAX_RETRIES) {
                    attempt++;
                    log.warn("YouTube API 호출 제한: operation={}, target='{}', status={}, attempt {}/{}. 재시도 대기",
                            operation, target, status, attempt, MAX_RETRIES);
                    backoff(attempt);
                    // 재시도
                } else if (YoutubeApiErrors.isRateLimit(status, body)) {
                    log.error("YouTube API 호출 제한 최종 실패: operation={}, target='{}', status={}, body={}",
                            operation, target, status, summarizeBody(body));
                    throw ex;
                } else if (YoutubeApiErrors.isQuotaExceeded(status, body)) {
                    log.error("YouTube API 쿼터 소진 의심: operation={}, target='{}', status={}, body={}",
                            operation, target, status, summarizeBody(body));
                    throw ex;
                } else {
                    log.error("YouTube API 오류: operation={}, target='{}', status={}, body={}",
                            operation, target, status, summarizeBody(body));
                    throw ex;
                }
            }
        }
    }

    private YoutubeSearchResponse doSearch(String q, int maxResults, String order, String apiKey,
                                           String publishedAfter, String publishedBefore, String pageToken,
                                           String videoDuration) {
        return youtubeRestClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("q", q)              // 원문 '|' → %7C (한 번만)
                            .queryParam("maxResults", maxResults)
                            .queryParam("order", order)
                            .queryParam("key", apiKey);
                    if (StringUtils.hasText(publishedAfter)) {
                        uriBuilder.queryParam("publishedAfter", publishedAfter);
                    }
                    if (StringUtils.hasText(publishedBefore)) {
                        uriBuilder.queryParam("publishedBefore", publishedBefore);
                    }
                    if (StringUtils.hasText(pageToken)) {
                        uriBuilder.queryParam("pageToken", pageToken);
                    }
                    if (StringUtils.hasText(videoDuration) && !"any".equalsIgnoreCase(videoDuration)) {
                        uriBuilder.queryParam("videoDuration", videoDuration);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(YoutubeSearchResponse.class);
    }

    private YoutubeChannelResponse doChannel(String filterName, String filterValue, String apiKey) {
        return youtubeRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/channels")
                        .queryParam("part", "snippet,contentDetails")
                        .queryParam(filterName, filterValue)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(YoutubeChannelResponse.class);
    }

    private YoutubePlaylistItemsResponse doPlaylistItems(String playlistId, int maxResults, String apiKey, String pageToken) {
        return youtubeRestClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/playlistItems")
                            .queryParam("part", "snippet,contentDetails")
                            .queryParam("playlistId", playlistId)
                            .queryParam("maxResults", maxResults)
                            .queryParam("key", apiKey);
                    if (StringUtils.hasText(pageToken)) {
                        uriBuilder.queryParam("pageToken", pageToken);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(YoutubePlaylistItemsResponse.class);
    }

    private void backoff(int attempt) {
        long millis = retryBaseMillis * (1L << (attempt - 1));   // base, 2*base, 4*base
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트", e);
        }
    }

    private String summarizeBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ");
        if (normalized.length() > 500) {
            return normalized.substring(0, 500) + "...";
        }
        return normalized;
    }
}
