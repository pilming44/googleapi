package com.example.ytclassifier.web;

import com.example.ytclassifier.domain.ClassificationStatus;
import com.example.ytclassifier.domain.VideoClassification;
import com.example.ytclassifier.domain.YoutubeVideo;
import com.example.ytclassifier.repository.SearchRunItemRepository;
import com.example.ytclassifier.repository.VideoClassificationRepository;
import com.example.ytclassifier.repository.YoutubeVideoRepository;
import com.example.ytclassifier.service.YoutubeSearchClient;
import com.example.ytclassifier.service.dto.YoutubeSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 화면 렌더링/상호작용 검증(요구사항 4~8): 카드의 워치 URL·상태 버튼·설명 150자 절단, AJAX 상태 변경,
 * 그리고 검색 후 폼 입력값이 세션으로 유지되는지.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VideoWebTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    YoutubeVideoRepository videoRepository;
    @Autowired
    VideoClassificationRepository classificationRepository;
    @Autowired
    SearchRunItemRepository searchRunItemRepository;
    @MockitoBean
    YoutubeSearchClient youtubeSearchClient;

    @BeforeEach
    void clean() {
        searchRunItemRepository.deleteAll();
        videoRepository.deleteAll();
    }

    private void seed(String videoId, String title, String description, ClassificationStatus status) {
        YoutubeVideo video = new YoutubeVideo(videoId);
        video.setTitle(title);
        video.setDescription(description);
        video.setChannelTitle("채널");
        video.setThumbnailUrl("http://img/" + videoId);
        video.setPublishedAt(Instant.parse("2024-01-02T03:04:05Z"));
        video.setClassification(new VideoClassification(video, status, Instant.now()));
        videoRepository.save(video);
    }

    @Test
    void videosPage_rendersCard_withWatchUrl_statusButtons_andTruncatedDescription() throws Exception {
        String longDesc = "a".repeat(150) + "ZZZTAIL";   // 150자 초과 → 절단되어 ZZZTAIL은 보이지 않아야 함
        seed("vid123", "테스트 제목", longDesc, ClassificationStatus.UNCLASSIFIED);

        mockMvc.perform(get("/videos").param("status", "UNCLASSIFIED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://www.youtube.com/watch?v=vid123")))
                .andExpect(content().string(containsString("target=\"_blank\"")))
                .andExpect(content().string(containsString("data-status=\"KEEP\"")))
                .andExpect(content().string(containsString("data-status=\"HOLD\"")))
                .andExpect(content().string(containsString("data-status=\"EXCLUDE\"")))
                .andExpect(content().string(containsString("...")))
                .andExpect(content().string(not(containsString("ZZZTAIL"))));
    }

    @Test
    void changeStatus_updatesDbAndReturnsCounts() throws Exception {
        seed("vidX", "제목", "설명", ClassificationStatus.UNCLASSIFIED);

        mockMvc.perform(post("/api/videos/vidX/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"KEEP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.KEEP").value(1))
                .andExpect(jsonPath("$.counts.UNCLASSIFIED").value(0));

        assertThat(classificationRepository.findById("vidX"))
                .get()
                .extracting(VideoClassification::getStatus)
                .isEqualTo(ClassificationStatus.KEEP);
    }

    @Test
    void searchForm_isPreservedInSession_afterSearch() throws Exception {
        given(youtubeSearchClient.search(any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .willReturn(new YoutubeSearchResponse(null, null, List.of()));   // 빈 결과(검색은 성공)

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/search").session(session)
                        .param("keywords", "고양이")
                        .param("apiKey", "my-key")
                        .param("maxResults", "30")
                        .param("targetCount", "200")
                        .param("order", "date")
                        .param("videoDuration", "short"))
                .andExpect(status().is3xxRedirection());

        // 같은 세션으로 다시 진입하면 입력값이 그대로 채워져 있어야 함
        mockMvc.perform(get("/videos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("고양이")))
                .andExpect(content().string(containsString("my-key")))
                .andExpect(content().string(containsString("value=\"200\"")));
    }
}
