package com.example.ytclassifier.web;

import com.example.ytclassifier.service.VideoClassificationService;
import com.example.ytclassifier.web.dto.StatusChangeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 카드 상태 변경 AJAX 엔드포인트(요구사항 6~8). 변경 후 갱신된 상태별 건수를 반환해
 * 프론트가 탭 뱃지를 즉시 갱신하고 카드를 화면에서 제거한다.
 */
@RestController
@RequestMapping("/api/videos")
public class VideoStatusRestController {

    private final VideoClassificationService classificationService;

    public VideoStatusRestController(VideoClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @PostMapping("/{videoId}/status")
    public ResponseEntity<?> changeStatus(@PathVariable String videoId,
                                          @RequestBody StatusChangeRequest request) {
        if (request == null || request.status() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "status는 필수입니다."));
        }
        try {
            classificationService.changeStatus(videoId, request.status());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("counts", classificationService.counts()));
    }
}
