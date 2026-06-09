package com.example.ytclassifier.service;

import com.example.ytclassifier.config.RestClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 네트워크 없이 RestClient 코드 경로를 그대로 타되, 인터셉터로 요청/응답을 가로채 검증한다.
 */
class YoutubeSearchClientTest {

    private static MockClientHttpResponse json(String body, HttpStatus status) {
        MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response;
    }

    /** 요청 URI를 캡처하고 빈 200 응답을 돌려주는 클라이언트. */
    private YoutubeSearchClient capturingClient(AtomicReference<URI> captured) {
        RestClient restClient = RestClient.builder()
                .baseUrl(RestClientConfig.YOUTUBE_API_BASE_URL)
                .requestInterceptor((request, body, execution) -> {
                    captured.set(request.getURI());
                    return json("{}", HttpStatus.OK);
                })
                .build();
        return new YoutubeSearchClient(restClient);
    }

    @Test
    void pipeIsEncodedExactlyOnce() {
        AtomicReference<URI> captured = new AtomicReference<>();
        capturingClient(captured).search("cats|dogs", 25, "date", "test-key", null, null, null, null);

        URI uri = captured.get();
        assertThat(uri).isNotNull();
        assertThat(uri.getRawQuery()).contains("q=cats%7Cdogs");   // %7C, NOT %257C
        assertThat(uri.getRawQuery()).doesNotContain("%257C");
    }

    @Test
    void videoDurationIsSentWhenSet() {
        AtomicReference<URI> captured = new AtomicReference<>();
        capturingClient(captured).search("cats", 25, "date", "test-key", null, null, null, "medium");
        assertThat(captured.get().getRawQuery()).contains("videoDuration=medium");
    }

    @Test
    void videoDurationIsOmittedForAny() {
        AtomicReference<URI> captured = new AtomicReference<>();
        capturingClient(captured).search("cats", 25, "date", "test-key", null, null, null, "any");
        assertThat(captured.get().getRawQuery()).doesNotContain("videoDuration");
    }

    @Test
    void retriesOnRateLimit_thenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        RestClient restClient = RestClient.builder()
                .baseUrl(RestClientConfig.YOUTUBE_API_BASE_URL)
                .requestInterceptor((request, body, execution) -> {
                    int n = calls.incrementAndGet();
                    return (n < 3) ? json("{}", HttpStatus.TOO_MANY_REQUESTS) : json("{}", HttpStatus.OK);
                })
                .build();
        YoutubeSearchClient client = new YoutubeSearchClient(restClient);
        client.setRetryBaseMillis(0);   // 테스트에서 백오프 제거

        assertThat(client.search("cats", 25, "date", "k", null, null, null, null)).isNotNull();
        assertThat(calls.get()).isEqualTo(3);   // 2회 재시도 후 성공
    }

    @Test
    void doesNotRetryOnQuotaExceeded() {
        AtomicInteger calls = new AtomicInteger();
        RestClient restClient = RestClient.builder()
                .baseUrl(RestClientConfig.YOUTUBE_API_BASE_URL)
                .requestInterceptor((request, body, execution) -> {
                    calls.incrementAndGet();
                    return json("{\"error\":{\"errors\":[{\"reason\":\"quotaExceeded\"}]}}", HttpStatus.FORBIDDEN);
                })
                .build();
        YoutubeSearchClient client = new YoutubeSearchClient(restClient);
        client.setRetryBaseMillis(0);

        assertThatThrownBy(() -> client.search("cats", 25, "date", "k", null, null, null, null))
                .isInstanceOf(HttpClientErrorException.class);
        assertThat(calls.get()).isEqualTo(1);   // 재시도 없음
    }
}
