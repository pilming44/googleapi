package com.example.ytclassifier.service;

import com.example.ytclassifier.domain.ClassificationStatus;
import com.example.ytclassifier.domain.SearchRun;
import com.example.ytclassifier.repository.SearchRunItemRepository;
import com.example.ytclassifier.repository.SearchRunRepository;
import com.example.ytclassifier.repository.VideoClassificationRepository;
import com.example.ytclassifier.repository.YoutubeVideoRepository;
import com.example.ytclassifier.exception.SearchException;
import com.example.ytclassifier.service.dto.YoutubeSearchResponse;
import com.example.ytclassifier.web.dto.SearchForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 검색 오케스트레이션 검증: 목표 건수 수집, 실행 내/DB 중복 처리, 기존 상태 보존,
 * 기간(publishedBefore) 자동 확장 슬라이딩, API 오류 기록.
 */
@SpringBootTest
class SearchServiceTest {

    @MockitoBean
    YoutubeSearchClient youtubeSearchClient;

    @Autowired
    SearchService searchService;
    @Autowired
    VideoClassificationService classificationService;
    @Autowired
    YoutubeVideoRepository videoRepository;
    @Autowired
    VideoClassificationRepository classificationRepository;
    @Autowired
    SearchRunRepository searchRunRepository;
    @Autowired
    SearchRunItemRepository searchRunItemRepository;

    @BeforeEach
    void clean() {
        searchRunItemRepository.deleteAll();
        videoRepository.deleteAll();   // cascade로 분류도 제거
        searchRunRepository.deleteAll();
    }

    private SearchForm form(int targetCount, int maxResults) {
        SearchForm form = new SearchForm();
        form.setKeywords("cats, dogs");
        form.setMaxResults(maxResults);
        form.setTargetCount(targetCount);
        form.setOrder("date");
        form.setVideoDuration("any");
        return form;
    }

    private static YoutubeSearchResponse.Item item(String id, String publishedAt) {
        YoutubeSearchResponse.Snippet snippet = new YoutubeSearchResponse.Snippet(
                "title " + id, "desc " + id, "chId", "channel", publishedAt,
                new YoutubeSearchResponse.Thumbnails(
                        null, new YoutubeSearchResponse.Thumb("http://img/" + id, 320, 180), null));
        return new YoutubeSearchResponse.Item(new YoutubeSearchResponse.Id(id), snippet);
    }

    private static YoutubeSearchResponse pageItems(String nextToken, YoutubeSearchResponse.Item... items) {
        return new YoutubeSearchResponse(nextToken, new YoutubeSearchResponse.PageInfo(100, items.length), List.of(items));
    }

    private static YoutubeSearchResponse page(String nextToken, String... ids) {
        YoutubeSearchResponse.Item[] items = Arrays.stream(ids)
                .map(id -> item(id, "2024-01-01T00:00:00Z"))
                .toArray(YoutubeSearchResponse.Item[]::new);
        return pageItems(nextToken, items);
    }

    /** page1(token=null) → a,b,c (nextPageToken=P2), page2(token=P2) → c(중복),d (nextPageToken=null) */
    private void stubTwoPages() {
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull()))
                .willReturn(page("P2", "a", "b", "c"));
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), eq("P2"), isNull()))
                .willReturn(page(null, "c", "d"));
    }

    @Test
    void collectsUpToTarget_savingUnclassified_dedupingWithinRun() {
        stubTwoPages();

        SearchRun run = searchService.runSearch(form(4, 50), "test-key");

        assertThat(run.getNewVideoCount()).isEqualTo(4);          // a,b,c,d
        assertThat(run.getDuplicatedVideoCount()).isEqualTo(0);
        assertThat(run.getTotalFetchedCount()).isEqualTo(4);      // 실행 내 중복(두 번째 c) 제외
        assertThat(run.getPageCount()).isEqualTo(2);              // search.list 호출 2회
        assertThat(run.getResultNote()).isEqualTo("목표 달성");
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getErrorMessage()).isNull();

        assertThat(videoRepository.count()).isEqualTo(4);
        assertThat(searchRunItemRepository.count()).isEqualTo(4);
        assertThat(classificationRepository.findAll())
                .allMatch(c -> c.getStatus() == ClassificationStatus.UNCLASSIFIED);

        verify(youtubeSearchClient, times(1))
                .search(anyString(), anyInt(), anyString(), anyString(), isNull(), isNull(), isNull(), isNull());
        verify(youtubeSearchClient, times(1))
                .search(anyString(), anyInt(), anyString(), anyString(), isNull(), isNull(), eq("P2"), isNull());
    }

    @Test
    void existingVideos_areCountedAsDuplicate_andStatusPreserved() {
        stubTwoPages();

        searchService.runSearch(form(4, 50), "test-key");        // 최초: 4건 신규
        classificationService.changeStatus("a", ClassificationStatus.KEEP);

        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), eq("2024-01-02T00:00:00Z"), isNull(), isNull()))
                .willReturn(page(null, "a", "b", "c", "d"));

        SearchRun run2 = searchService.runSearch(form(4, 50), "test-key");  // 동일 검색 재실행

        assertThat(run2.getNewVideoCount()).isEqualTo(0);
        assertThat(run2.getDuplicatedVideoCount()).isEqualTo(4);
        assertThat(videoRepository.count()).isEqualTo(4);        // 신규 저장 없음
        assertThat(classificationRepository.findById("a"))
                .get()
                .extracting(c -> c.getStatus())
                .isEqualTo(ClassificationStatus.KEEP);           // 기존 상태 유지(요구사항 3)
    }

    @Test
    void slidesPublishedBefore_toCollectBeyondOneWindow() {
        // 창1: v1~v4 (가장 오래된 v4 = 2024-03-07), 창2: publishedBefore=2024-03-07 → v5,v6
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull()))
                .willReturn(pageItems("W1P2", item("v1", "2024-03-10T00:00:00Z"), item("v2", "2024-03-09T00:00:00Z")));
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), eq("W1P2"), isNull()))
                .willReturn(pageItems(null, item("v3", "2024-03-08T00:00:00Z"), item("v4", "2024-03-07T00:00:00Z")));
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), eq("2024-03-07T00:00:01Z"), isNull(), isNull()))
                .willReturn(pageItems(null, item("v5", "2024-03-06T00:00:00Z"), item("v6", "2024-03-05T00:00:00Z")));

        SearchRun run = searchService.runSearch(form(6, 2), "test-key");

        assertThat(run.getNewVideoCount()).isEqualTo(6);
        assertThat(run.getDuplicatedVideoCount()).isEqualTo(0);
        assertThat(run.getPageCount()).isEqualTo(3);             // 창1 2페이지 + 창2 1페이지
        assertThat(run.getResultNote()).isEqualTo("목표 달성");
        assertThat(videoRepository.count()).isEqualTo(6);

        // 3번째 호출은 publishedBefore가 창1의 가장 오래된 게시일로 내려가야 함
        verify(youtubeSearchClient, times(1))
                .search(anyString(), anyInt(), anyString(), anyString(),
                        isNull(), eq("2024-03-07T00:00:01Z"), isNull(), isNull());
    }

    @Test
    void quotaExceeded_isRecordedWithQuotaNote_andRethrown() {
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull()))
                .willThrow(HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY,
                        "{\"error\":{\"errors\":[{\"reason\":\"quotaExceeded\"}]}}".getBytes(StandardCharsets.UTF_8), null));

        assertThatThrownBy(() -> searchService.runSearch(form(50, 50), "test-key"))
                .isInstanceOf(SearchException.class)
                .hasMessageContaining("쿼터");

        List<SearchRun> runs = searchRunRepository.findAll();
        assertThat(runs).hasSize(1);
        SearchRun run = runs.get(0);
        assertThat(run.getResultNote()).isEqualTo("쿼터 소진");
        assertThat(run.getErrorMessage()).contains("쿼터");
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(videoRepository.count()).isEqualTo(0);
    }

    @Test
    void rateLimit_isRecordedWithRateLimitNote() {
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull()))
                .willThrow(HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY,
                        new byte[0], null));

        assertThatThrownBy(() -> searchService.runSearch(form(50, 50), "test-key"))
                .isInstanceOf(SearchException.class);

        SearchRun run = searchRunRepository.findAll().get(0);
        assertThat(run.getResultNote()).isEqualTo("호출 제한 지속");
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    void resume_continuesFromLatestRunByDateBoundary() {
        // 1차: v1~v4 (가장 오래된 v4=2024-03-07), 한 페이지로 목표 충족
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull()))
                .willReturn(pageItems(null,
                        item("v1", "2024-03-10T00:00:00Z"), item("v2", "2024-03-09T00:00:00Z"),
                        item("v3", "2024-03-08T00:00:00Z"), item("v4", "2024-03-07T00:00:00Z")));
        searchService.runSearch(form(4, 50), "test-key");
        assertThat(videoRepository.count()).isEqualTo(4);

        // 2차(이어서): publishedBefore = 가장 오래된 수집일의 다음 날(2024-03-08)로 이어받아 v5,v6 수집
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), eq("2024-03-08T00:00:00Z"), isNull(), isNull()))
                .willReturn(pageItems(null,
                        item("v5", "2024-03-06T00:00:00Z"), item("v6", "2024-03-05T00:00:00Z")));
        SearchForm resumeForm = form(2, 50);
        SearchRun run = searchService.runSearch(resumeForm, "test-key");

        assertThat(run.getNewVideoCount()).isEqualTo(2);
        assertThat(videoRepository.count()).isEqualTo(6);
        verify(youtubeSearchClient, times(1))
                .search(anyString(), anyInt(), anyString(), anyString(),
                        isNull(), eq("2024-03-08T00:00:00Z"), isNull(), isNull());
    }

    @Test
    void resume_uses_latest_run_not_all_history() {
        // 1차 실행(오래된 실행): 2024-01-10(최저)
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull()))
                .willReturn(
                        pageItems(null, item("old1", "2024-01-15T00:00:00Z"),
                                item("old2", "2024-01-10T00:00:00Z")),
                        pageItems(null, item("new1", "2024-03-10T00:00:00Z"),
                                item("new2", "2024-03-01T00:00:00Z")));

        searchService.runSearch(form(2, 50), "test-key");
        searchService.runSearch(form(2, 50), "test-key");

        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), eq("2024-03-02T00:00:00Z"), isNull(), isNull()))
                .willReturn(pageItems(null, item("later1", "2024-02-01T00:00:00Z")));

        SearchForm resumeForm = form(1, 50);
        searchService.runSearch(resumeForm, "test-key");

        verify(youtubeSearchClient, times(1))
                .search(anyString(), anyInt(), anyString(), anyString(),
                        isNull(), eq("2024-03-02T00:00:00Z"), isNull(), isNull());
    }

    @Test
    void notShort_collectsAcrossMediumAndLongBuckets() {
        SearchForm form = form(4, 50);
        form.setVideoDuration("notshort");   // 4분 이상 = medium + long 두 버킷

        // medium 창1: m1,m2 (가장 오래된 m2=2024-03-09)
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull(), eq("medium")))
                .willReturn(pageItems(null, item("m1", "2024-03-10T00:00:00Z"), item("m2", "2024-03-09T00:00:00Z")));
        // medium 창2(이어서 2024-03-09): 더 없음 → 빈 페이지 → medium 버킷 종료
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), eq("2024-03-09T00:00:01Z"), isNull(), eq("medium")))
                .willReturn(pageItems(null));
        // long 창1: l1,l2 → 목표(4) 달성
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull(), eq("long")))
                .willReturn(pageItems(null, item("l1", "2024-02-10T00:00:00Z"), item("l2", "2024-02-09T00:00:00Z")));

        SearchRun run = searchService.runSearch(form, "test-key");

        assertThat(run.getNewVideoCount()).isEqualTo(4);     // m1,m2,l1,l2
        assertThat(videoRepository.count()).isEqualTo(4);
        verify(youtubeSearchClient, times(1))
                .search(anyString(), anyInt(), anyString(), anyString(), isNull(), isNull(), isNull(), eq("medium"));
        verify(youtubeSearchClient, times(1))
                .search(anyString(), anyInt(), anyString(), anyString(), isNull(), isNull(), isNull(), eq("long"));
    }

    @Test
    void autoSplit_queriesEachYearSliceNewestFirst() {
        // 모든 호출 빈 결과(검색 성공) → 각 슬라이스에서 신규 0 → 다음 슬라이스로
        given(youtubeSearchClient.search(anyString(), anyInt(), anyString(), anyString(),
                any(), any(), any(), any()))
                .willReturn(pageItems(null));

        SearchForm form = form(100, 50);
        form.setPublishedAfter("2022-01-01");
        form.setPublishedBefore("2023-12-31");

        searchService.runSearch(form, "test-key");

        // 2023 슬라이스(최신) + 2022 슬라이스 각각의 [publishedAfter, publishedBefore] 로 호출되어야 함
        verify(youtubeSearchClient, times(1))
                .search(anyString(), anyInt(), anyString(), anyString(),
                        eq("2023-01-01T00:00:00Z"), eq("2024-01-01T00:00:00Z"), isNull(), isNull());
        verify(youtubeSearchClient, times(1))
                .search(anyString(), anyInt(), anyString(), anyString(),
                        eq("2022-01-01T00:00:00Z"), eq("2023-01-01T00:00:00Z"), isNull(), isNull());
    }
}
