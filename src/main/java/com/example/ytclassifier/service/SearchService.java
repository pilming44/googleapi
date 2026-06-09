package com.example.ytclassifier.service;

import com.example.ytclassifier.domain.ClassificationStatus;
import com.example.ytclassifier.domain.SearchRun;
import com.example.ytclassifier.domain.SearchRunItem;
import com.example.ytclassifier.domain.VideoClassification;
import com.example.ytclassifier.domain.YoutubeVideo;
import com.example.ytclassifier.exception.SearchException;
import com.example.ytclassifier.repository.SearchRunItemRepository;
import com.example.ytclassifier.repository.SearchRunRepository;
import com.example.ytclassifier.repository.YoutubeVideoRepository;
import com.example.ytclassifier.service.dto.YoutubeChannelResponse;
import com.example.ytclassifier.service.dto.YoutubePlaylistItemsResponse;
import com.example.ytclassifier.service.dto.YoutubeSearchResponse;
import com.example.ytclassifier.web.dto.SearchForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 검색 실행 오케스트레이션(요구사항 1~3, 10, 11).
 *
 * <p>수집은 (기간 슬라이스) × (길이 버킷) × (창 슬라이딩) × (페이지) 4중 루프로 이뤄진다.
 * <ul>
 *   <li>창 슬라이딩: date 정렬에서 한 창이 소진되면 publishedBefore를 "그 창의 가장 오래된 게시시각 + 1초"로
 *       내려 더 과거를 조회한다. +1초로 겹쳐 같은 '초'에 올라온 영상이 경계에서 누락되지 않게 한다(중복은 제거).</li>
 *   <li>길이 버킷: "4분 이상"은 medium+long 두 버킷을 합쳐 수집한다.</li>
 *   <li>기간 자동 분할(autoSplit): 큰 날짜 범위를 연 단위로 쪼개 각 구간을 독립 쿼리로 수집한다
 *       (구간마다 ~500 예산을 별도로 받아 단일 범위 천장을 넘김). 이 모드에서는 '이어서 수집'은 무시한다.</li>
 *   <li>목표 달성 / 신규 소진 / 안전 한도(API 호출수) 중 하나면 종료.</li>
 * </ul>
 *
 * <p>트랜잭션: 의도적으로 @Transactional 로 묶지 않는다(단일 로컬 사용자). 각 저장이 즉시 커밋되어
 * 중간 실패 시에도 이전 결과와 SearchRun 이력이 남는다.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private static final int MAX_RESULTS_LIMIT = 50;
    private static final int MIN_RESULTS = 1;
    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final int DEFAULT_TARGET = 50;
    private static final String DEFAULT_ORDER = "date";
    private static final int ERROR_MESSAGE_LIMIT = 1900;
    /** 한 창(같은 publishedBefore) 안에서의 최대 페이지 수(백스톱; 보통 ~500건에서 토큰이 끊김). */
    private static final int MAX_PAGES_PER_WINDOW = 12;
    /** 우선 수집(채널 업로드) 경로로 저장된 영상의 버킷 라벨. 키워드 길이 버킷별 커서와 섞이지 않게 구분한다. */
    private static final String CHANNEL_BUCKET = "channel";

    private final YoutubeSearchClient youtubeSearchClient;
    private final YoutubeVideoRepository videoRepository;
    private final SearchRunRepository searchRunRepository;
    private final SearchRunItemRepository searchRunItemRepository;
    private final int maxTarget;
    private final int maxApiCalls;
    private final int maxChannelApiCalls;

    public SearchService(YoutubeSearchClient youtubeSearchClient,
                         YoutubeVideoRepository videoRepository,
                         SearchRunRepository searchRunRepository,
                         SearchRunItemRepository searchRunItemRepository,
                         @Value("${youtube.collect.max-target:5000}") int maxTarget,
                         @Value("${youtube.collect.max-api-calls:100}") int maxApiCalls,
                         @Value("${youtube.collect.max-channel-api-calls:1000}") int maxChannelApiCalls) {
        this.youtubeSearchClient = youtubeSearchClient;
        this.videoRepository = videoRepository;
        this.searchRunRepository = searchRunRepository;
        this.searchRunItemRepository = searchRunItemRepository;
        this.maxTarget = maxTarget;
        this.maxApiCalls = maxApiCalls;
        this.maxChannelApiCalls = maxChannelApiCalls;
    }

    /** 수집 진행 상태(슬라이스/버킷/창을 가로질러 공유). */
    private static final class Collector {
        int totalFetched;
        int newCount;
        int dupCount;
        int rank;
        int searchApiCalls;
        int channelApiCalls;
        final Set<String> seen = new HashSet<>();
        String resultNote;
        boolean stop;   // 목표/안전한도 도달 → 전체 종료

        int totalApiCalls() {
            return searchApiCalls + channelApiCalls;
        }
    }

    private record PriorityChannel(String input, String channelId, String title, String uploadsPlaylistId) {
    }

    public SearchRun runSearch(SearchForm form, String effectiveApiKey) {
        List<String> keywords = form.keywordList();
        List<String> priorityChannels = form.priorityChannelList();
        if (keywords.isEmpty() && priorityChannels.isEmpty()) {
            throw new SearchException("검색 키워드 또는 우선 수집 채널을 1개 이상 입력하세요.");
        }
        if (!StringUtils.hasText(effectiveApiKey)) {
            throw new SearchException("API Key를 입력하세요.");
        }

        int maxResults = clamp(form.getMaxResults(), DEFAULT_MAX_RESULTS, MIN_RESULTS, MAX_RESULTS_LIMIT);
        int targetCount = clamp(form.getTargetCount(), DEFAULT_TARGET, 1, maxTarget);
        String order = StringUtils.hasText(form.getOrder()) ? form.getOrder() : DEFAULT_ORDER;
        String durationMode = normalizeDurationMode(form.getVideoDuration());   // any | short | notshort
        List<String> buckets = resolveBuckets(durationMode);                    // [null] | [short] | [medium,long]
        String q = String.join("|", keywords);                                  // OR 검색(요구사항 1)
        String runQuery = StringUtils.hasText(q) ? q : "[channels]";
        boolean hasKeywordSearch = !keywords.isEmpty();
        boolean canSlide = DEFAULT_ORDER.equals(order);                         // 기간 자동확장/분할은 date 정렬에서만

        String publishedAfter = toRfc3339StartOfDay(form.getPublishedAfter());      // 하한(고정)
        String publishedBefore = toRfc3339StartOfNextDay(form.getPublishedBefore()); // 초기 상한

        // 기본 동작: 조회 범위를 지정해 두면 연 단위 구간으로 분할해 누락 가능성을 줄인다.
        boolean autoSplit = canSlide && publishedAfter != null && publishedBefore != null;

        // 수집 대상 기간 슬라이스(autoSplit이면 연 단위, 아니면 전체 1개), 최신 구간부터.
        List<String[]> slices;
        if (autoSplit) {
            slices = yearlySlices(Instant.parse(publishedAfter), Instant.parse(publishedBefore));
        } else {
            slices = Collections.singletonList(new String[]{publishedAfter, publishedBefore});
        }

        // 1) 실행 이력 먼저 저장 — 이후 실패해도 흔적이 남는다. (API Key 원문은 저장하지 않음)
        SearchRun run = new SearchRun();
        run.setQuery(runQuery);
        run.setApiKeyAlias(null);
        run.setMaxResults(maxResults);
        run.setTargetCount(targetCount);
        run.setOrderType(order);
        run.setVideoDuration(durationMode);
        run.setStartedAt(Instant.now());
        run = searchRunRepository.save(run);

        Collector st = new Collector();
        try {
            if (!priorityChannels.isEmpty()) {
                collectPriorityChannels(st, run, priorityChannels, maxResults, effectiveApiKey);
            }

            if (hasKeywordSearch && !st.stop) {
                int keywordTargetLimit = st.newCount + targetCount;
                outer:
                for (String[] slice : slices) {
                    for (String bucket : buckets) {
                        // 버킷별 '이어서 수집': 각 길이 버킷(any/short/medium/long)이 자신의 최古 게시일부터
                        // 독립적으로 재개한다. (autoSplit 기간 분할 모드에서는 고정 슬라이스 경계를 그대로 쓴다.)
                        String effectiveBefore = slice[1];
                        if (canSlide && !autoSplit) {
                            Instant cursor = searchRunItemRepository
                                    .findOldestCollectedPublishedAtForBucket(q, bucketLabel(bucket), order);
                            if (cursor != null) {
                                effectiveBefore = cursor.atZone(ZoneOffset.UTC)
                                        .toLocalDate()
                                        .plusDays(1)
                                        .atStartOfDay(ZoneOffset.UTC)
                                        .toInstant()
                                        .toString();
                            }
                        }
                        collectChain(st, run, q, maxResults, order, effectiveApiKey, bucket,
                                slice[0], effectiveBefore, keywordTargetLimit, canSlide);
                        if (st.stop) {
                            break outer;
                        }
                    }
                }
            }

            String note = st.stop ? st.resultNote
                    : (!hasKeywordSearch ? "채널 우선 수집 완료"
                    : (autoSplit ? "지정 기간 수집 완료" : (st.resultNote != null ? st.resultNote : "수집 완료")));
            run.setTotalFetchedCount(st.totalFetched);
            run.setNewVideoCount(st.newCount);
            run.setDuplicatedVideoCount(st.dupCount);
            run.setPageCount(st.totalApiCalls());
            run.setResultNote(note);
            run.setFinishedAt(Instant.now());
            return searchRunRepository.save(run);

        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String body = ex.getResponseBodyAsString();
            String message;
            String note;
            if (YoutubeApiErrors.isQuotaExceeded(status, body)) {
                message = "YouTube 일일 쿼터를 모두 사용했습니다(403). 내일 다시 시도하거나 '이어서 수집'으로 이어가세요.";
                note = "쿼터 소진";
            } else if (YoutubeApiErrors.isRateLimit(status, body)) {
                message = "YouTube 호출 제한이 계속됩니다(자동 재시도 후에도 실패). 잠시 후 다시 시도하세요.";
                note = "호출 제한 지속";
            } else {
                message = "YouTube API 오류(" + status + "): " + cut(body, ERROR_MESSAGE_LIMIT);
                note = "오류 발생";
            }
            log.error("SearchService API 예외: query={}, order={}, duration={}, status={}, errorMessage={}", runQuery, order, durationMode, status, cut(body, ERROR_MESSAGE_LIMIT));
            recordFailure(run, st, message, note);
            throw new SearchException(message, ex);
        } catch (SearchException ex) {
            log.warn("SearchService 유효성/비즈니스 예외: query={}, order={}, duration={}, message={}", runQuery, order, durationMode, cut(ex.getMessage(), ERROR_MESSAGE_LIMIT));
            recordFailure(run, st, cut(ex.getMessage(), ERROR_MESSAGE_LIMIT), "오류 발생");
            throw ex;
        } catch (Exception ex) {
            String msg = "검색 처리 중 오류: " + cut(ex.getMessage(), ERROR_MESSAGE_LIMIT);
            log.error("SearchService 예기치 못한 예외: query={}, order={}, duration={}, message={}", runQuery, order, durationMode, cut(ex.getMessage(), ERROR_MESSAGE_LIMIT));
            recordFailure(run, st, msg, "오류 발생");
            throw new SearchException(msg, ex);
        }
    }

    private void collectPriorityChannels(Collector st, SearchRun run, List<String> channelInputs,
                                         int maxResults, String apiKey) {
        for (String channelInput : channelInputs) {
            if (st.stop) {
                return;
            }
            PriorityChannel channel = resolvePriorityChannel(st, channelInput, apiKey);
            log.info("우선 채널 수집 시작: input={}, channelId={}, title={}",
                    channel.input(), channel.channelId(), channel.title());

            String pageToken = null;
            int pageNo = 0;
            while (true) {
                if (st.channelApiCalls >= maxChannelApiCalls) {
                    st.resultNote = "채널 수집 안전 한도(API 호출 " + maxChannelApiCalls + "회) 도달";
                    st.stop = true;
                    return;
                }
                YoutubePlaylistItemsResponse resp = youtubeSearchClient.playlistItems(
                        channel.uploadsPlaylistId(), maxResults, apiKey, pageToken);
                st.channelApiCalls++;
                pageNo++;
                if (resp == null) {
                    return;
                }

                List<YoutubePlaylistItemsResponse.Item> items = resp.items() == null ? List.of() : resp.items();
                for (YoutubePlaylistItemsResponse.Item item : items) {
                    String videoId = playlistVideoId(item);
                    if (!StringUtils.hasText(videoId)) {
                        continue;
                    }
                    collectVideo(st, run, videoId, toSnippet(item), pageNo, CHANNEL_BUCKET);
                }

                pageToken = resp.nextPageToken();
                if (!StringUtils.hasText(pageToken)) {
                    break;
                }
            }
            log.info("우선 채널 수집 완료: input={}, channelId={}, title={}",
                    channel.input(), channel.channelId(), channel.title());
        }
    }

    private PriorityChannel resolvePriorityChannel(Collector st, String input, String apiKey) {
        String normalized = normalizeChannelInput(input);
        if (!StringUtils.hasText(normalized)) {
            throw new SearchException("우선 수집 채널 값이 비어 있습니다.");
        }

        YoutubeChannelResponse resp;
        if (normalized.startsWith("user:")) {
            resp = youtubeSearchClient.channelByUsername(normalized.substring("user:".length()), apiKey);
            st.channelApiCalls++;
        } else if (isChannelId(normalized)) {
            resp = youtubeSearchClient.channelById(normalized, apiKey);
            st.channelApiCalls++;
        } else {
            resp = youtubeSearchClient.channelByHandle(normalized, apiKey);
            st.channelApiCalls++;
            if (firstChannel(resp) == null && !normalized.startsWith("@")) {
                resp = youtubeSearchClient.channelByUsername(normalized, apiKey);
                st.channelApiCalls++;
            }
        }

        YoutubeChannelResponse.Item item = firstChannel(resp);
        if (item == null) {
            throw new SearchException("우선 수집 채널을 찾을 수 없습니다: " + input + " (채널 ID 또는 @handle을 입력하세요.)");
        }
        String uploads = item.contentDetails() == null || item.contentDetails().relatedPlaylists() == null
                ? null : item.contentDetails().relatedPlaylists().uploads();
        if (!StringUtils.hasText(uploads)) {
            throw new SearchException("우선 수집 채널의 업로드 목록을 찾을 수 없습니다: " + input);
        }
        String title = item.snippet() == null ? item.id() : item.snippet().title();
        return new PriorityChannel(input, item.id(), title, uploads);
    }

    /**
     * 한 (기간 슬라이스 × 길이 버킷)에 대해 페이지네이션 + 창 슬라이딩(+1초 겹침)으로 수집한다.
     * 목표/안전한도 도달 시 st.stop=true 로 표시하고 반환(상위에서 전체 종료).
     */
    private void collectChain(Collector st, SearchRun run, String q, int maxResults, String order,
                              String apiKey, String videoDurationBucket, String sliceAfter, String sliceBefore,
                              int targetCount, boolean canSlide) {
        String currentBefore = sliceBefore;
        String lastBefore = " ";   // 첫 비교에서 currentBefore(null 가능)와 같지 않도록 한 센티넬

        while (true) {
            if (Objects.equals(currentBefore, lastBefore)) {
                if (st.resultNote == null) {
                    st.resultNote = "더 이상 진행할 구간 없음";
                }
                return;   // 경계가 더 내려가지 않음 → 이 체인 종료(무한루프 방지)
            }
            lastBefore = currentBefore;

            String pageToken = null;
            int newInWindow = 0;
            int pageNoInWindow = 0;
            Instant windowOldest = null;

            while (true) {
                if (st.searchApiCalls >= maxApiCalls) {
                    st.resultNote = "안전 한도(API 호출 " + maxApiCalls + "회) 도달 — 목표 미달로 중단";
                    st.stop = true;
                    return;
                }
                YoutubeSearchResponse resp = youtubeSearchClient.search(
                        q, maxResults, order, apiKey, sliceAfter, currentBefore, pageToken, videoDurationBucket);
                st.searchApiCalls++;
                if (resp == null) {
                    break;
                }
                pageNoInWindow++;

                List<YoutubeSearchResponse.Item> items = resp.items() == null ? List.of() : resp.items();
                for (YoutubeSearchResponse.Item item : items) {
                    String videoId = (item.id() == null) ? null : item.id().videoId();
                    if (!StringUtils.hasText(videoId)) {
                        continue;
                    }
                    Instant published = parseInstant(item.snippet() == null ? null : item.snippet().publishedAt());
                    if (published != null && (windowOldest == null || published.isBefore(windowOldest))) {
                        windowOldest = published;
                    }
                    if (collectVideo(st, run, videoId, item.snippet(), pageNoInWindow, bucketLabel(videoDurationBucket))) {
                        newInWindow++;
                    }

                    if (st.newCount >= targetCount) {
                        st.resultNote = "목표 달성";
                        st.stop = true;
                        return;
                    }
                }

                pageToken = resp.nextPageToken();
                if (!StringUtils.hasText(pageToken)) {
                    break;   // 이 창 소진
                }
                if (pageNoInWindow >= MAX_PAGES_PER_WINDOW) {
                    break;   // 백스톱
                }
            }

            if (!canSlide) {
                st.resultNote = "단일 창 조회 완료(정렬이 date가 아니면 기간 자동확장 안 함)";
                return;
            }
            if (newInWindow == 0 && windowOldest == null) {
                st.resultNote = "더 이상 신규 결과 없음";
                return;
            }
            if (windowOldest == null) {
                st.resultNote = "더 이상 결과 없음";
                return;
            }
            // +1초 겹침: 같은 '초'에 올라온 영상이 경계에서 누락되지 않게(중복은 제거됨).
            currentBefore = windowOldest.plusSeconds(1).toString();
        }
    }

    private void recordFailure(SearchRun run, Collector st, String message, String note) {
        run.setTotalFetchedCount(st.totalFetched);
        run.setNewVideoCount(st.newCount);
        run.setDuplicatedVideoCount(st.dupCount);
        run.setPageCount(st.totalApiCalls());
        run.setErrorMessage(message);
        run.setResultNote(note);
        run.setFinishedAt(Instant.now());
        searchRunRepository.save(run);
    }

    private boolean collectVideo(Collector st, SearchRun run, String videoId,
                                 YoutubeSearchResponse.Snippet snippet, int pageNo, String videoDurationBucket) {
        if (!StringUtils.hasText(videoId) || !st.seen.add(videoId)) {
            return false;
        }

        st.totalFetched++;
        YoutubeVideo video;
        boolean isNew = false;
        if (videoRepository.existsById(videoId)) {
            // 이미 저장된 영상 → 재저장하지 않고 기존 분류 상태 유지(요구사항 3)
            st.dupCount++;
            video = videoRepository.getReferenceById(videoId);
        } else {
            video = toEntity(videoId, snippet);
            video.setClassification(new VideoClassification(
                    video, ClassificationStatus.UNCLASSIFIED, Instant.now()));
            video = videoRepository.save(video);
            st.newCount++;
            isNew = true;
        }

        searchRunItemRepository.save(new SearchRunItem(run, video, st.rank++, pageNo, videoDurationBucket));
        return isNew;
    }

    private static YoutubeChannelResponse.Item firstChannel(YoutubeChannelResponse resp) {
        if (resp == null || resp.items() == null || resp.items().isEmpty()) {
            return null;
        }
        return resp.items().getFirst();
    }

    private static String playlistVideoId(YoutubePlaylistItemsResponse.Item item) {
        if (item == null) {
            return null;
        }
        if (item.contentDetails() != null && StringUtils.hasText(item.contentDetails().videoId())) {
            return item.contentDetails().videoId();
        }
        if (item.snippet() != null && item.snippet().resourceId() != null) {
            return item.snippet().resourceId().videoId();
        }
        return null;
    }

    private static YoutubeSearchResponse.Snippet toSnippet(YoutubePlaylistItemsResponse.Item item) {
        if (item == null || item.snippet() == null) {
            return null;
        }
        YoutubePlaylistItemsResponse.Snippet snippet = item.snippet();
        String publishedAt = item.contentDetails() != null && StringUtils.hasText(item.contentDetails().videoPublishedAt())
                ? item.contentDetails().videoPublishedAt()
                : snippet.publishedAt();
        return new YoutubeSearchResponse.Snippet(
                snippet.title(),
                snippet.description(),
                snippet.channelId(),
                snippet.channelTitle(),
                publishedAt,
                snippet.thumbnails());
    }

    /** [afterI, beforeI) 를 연 단위로 분할(최신 구간부터). 각 원소는 {publishedAfterRfc, publishedBeforeRfc}. */
    private static List<String[]> yearlySlices(Instant afterI, Instant beforeI) {
        List<String[]> out = new ArrayList<>();
        int startYear = LocalDate.ofInstant(afterI, ZoneOffset.UTC).getYear();
        int endYear = LocalDate.ofInstant(beforeI.minusSeconds(1), ZoneOffset.UTC).getYear();   // beforeI 배타적
        for (int y = startYear; y <= endYear; y++) {
            Instant ys = LocalDate.of(y, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant ye = LocalDate.of(y + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant a = ys.isBefore(afterI) ? afterI : ys;
            Instant b = ye.isAfter(beforeI) ? beforeI : ye;
            if (a.isBefore(b)) {
                out.add(new String[]{a.toString(), b.toString()});
            }
        }
        Collections.reverse(out);   // 최신 연도부터
        return out;
    }

    private YoutubeVideo toEntity(String videoId, YoutubeSearchResponse.Snippet snippet) {
        YoutubeVideo video = new YoutubeVideo(videoId);
        if (snippet == null) {
            video.setTitle("(제목 없음)");
            return video;
        }
        // YouTube는 title/description을 HTML 이스케이프해서 반환한다(&amp; &#39; 등) → 디코딩 후 저장.
        video.setTitle(cut(unescape(snippet.title(), "(제목 없음)"), 300));
        video.setDescription(cut(unescape(snippet.description(), ""), 5000));
        video.setChannelId(snippet.channelId());
        video.setChannelTitle(cut(unescape(snippet.channelTitle(), null), 200));
        video.setThumbnailUrl(pickThumbnail(snippet.thumbnails()));
        video.setPublishedAt(parseInstant(snippet.publishedAt()));
        return video;
    }

    private static String pickThumbnail(YoutubeSearchResponse.Thumbnails thumbnails) {
        if (thumbnails == null) {
            return null;
        }
        if (thumbnails.medium() != null) {
            return thumbnails.medium().url();
        }
        if (thumbnails.high() != null) {
            return thumbnails.high().url();
        }
        if (thumbnails.defaultThumb() != null) {
            return thumbnails.defaultThumb().url();
        }
        return null;
    }

    private static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** yyyy-MM-dd → 그 날 00:00:00Z(RFC3339). 비었으면 null. */
    private static String toRfc3339StartOfDay(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        } catch (DateTimeParseException e) {
            throw new SearchException("게시일(이후) 날짜 형식이 올바르지 않습니다: " + date);
        }
    }

    /** yyyy-MM-dd → 다음 날 00:00:00Z(종료일 포함). 비었으면 null. */
    private static String toRfc3339StartOfNextDay(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim()).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        } catch (DateTimeParseException e) {
            throw new SearchException("게시일(이전) 날짜 형식이 올바르지 않습니다: " + date);
        }
    }

    /** 폼 값을 길이 모드로 정규화: any | short(4분 미만) | notshort(4분 이상). */
    private static String normalizeDurationMode(String duration) {
        if (!StringUtils.hasText(duration)) {
            return "any";
        }
        return switch (duration.trim().toLowerCase()) {
            case "short" -> "short";
            case "notshort", "medium", "long" -> "notshort";   // 4분 이상
            default -> "any";
        };
    }

    /** 길이 모드를 실제 search.list videoDuration 버킷 목록으로 변환. any는 [null](파라미터 없음). */
    private static List<String> resolveBuckets(String mode) {
        return switch (mode) {
            case "short" -> List.of("short");
            case "notshort" -> List.of("medium", "long");   // 4분 이상 = 4~20분 + 20분 초과
            default -> Collections.singletonList(null);
        };
    }

    /** 길이 버킷의 저장/조회용 라벨. 필터 없음(any 모드)은 null 대신 "any"로 통일해 버킷별 커서 키로 쓴다. */
    private static String bucketLabel(String bucket) {
        return bucket == null ? "any" : bucket;
    }

    private static String normalizeChannelInput(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        String value = input.trim();
        if (value.startsWith("youtube.com/") || value.startsWith("www.youtube.com/")) {
            value = "https://" + value;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                URI uri = URI.create(value);
                String path = uri.getPath();
                if (!StringUtils.hasText(path)) {
                    return null;
                }
                String[] parts = path.split("/");
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    if (!StringUtils.hasText(part)) {
                        continue;
                    }
                    if (part.startsWith("@")) {
                        return part;
                    }
                    if ("channel".equals(part) && i + 1 < parts.length) {
                        return parts[i + 1];
                    }
                    if ("user".equals(part) && i + 1 < parts.length) {
                        return "user:" + parts[i + 1];
                    }
                    if ("c".equals(part) && i + 1 < parts.length) {
                        return parts[i + 1];
                    }
                }
            } catch (IllegalArgumentException e) {
                return value;
            }
        }
        return value;
    }

    private static boolean isChannelId(String value) {
        return StringUtils.hasText(value) && value.matches("UC[0-9A-Za-z_-]{20,}");
    }

    private static int clamp(Integer value, int defaultValue, int min, int max) {
        int v = (value == null) ? defaultValue : value;
        return Math.min(max, Math.max(min, v));
    }

    private static String unescape(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return HtmlUtils.htmlUnescape(value);
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
