package com.example.ytclassifier.service;

import com.example.ytclassifier.domain.ClassificationStatus;
import com.example.ytclassifier.domain.VideoClassification;
import com.example.ytclassifier.repository.VideoClassificationRepository;
import com.example.ytclassifier.web.projection.VideoCardView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 영상 분류 상태 조회/변경(요구사항 4, 6~8).
 */
@Service
@Transactional(readOnly = true)
public class VideoClassificationService {

    private final VideoClassificationRepository repository;

    public VideoClassificationService(VideoClassificationRepository repository) {
        this.repository = repository;
    }

    /** 특정 상태(탭)의 카드 목록. */
    public List<VideoCardView> cards(ClassificationStatus status) {
        return repository.findCardsByStatus(status);
    }

    /** 상태별 건수(탭 뱃지). 키는 상태 이름(String)으로 두어 Thymeleaf/JSON에서 쓰기 쉽게 한다. */
    public Map<String, Long> counts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (ClassificationStatus status : ClassificationStatus.values()) {
            result.put(status.name(), repository.countByStatus(status));
        }
        return result;
    }

    /** 상태 변경(요구사항 8). 변경 즉시 해당 탭으로 이동하는 동작은 프론트(AJAX)가 처리한다. */
    @Transactional
    public void changeStatus(String videoId, ClassificationStatus status) {
        VideoClassification classification = repository.findById(videoId)
                .orElseThrow(() -> new NoSuchElementException("영상을 찾을 수 없습니다: " + videoId));
        classification.setStatus(status);
        classification.setClassifiedAt(Instant.now());
        // 영속 상태 엔티티이므로 트랜잭션 커밋 시 자동 반영(dirty checking)
    }
}
