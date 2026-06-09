package com.example.ytclassifier.repository;

import com.example.ytclassifier.domain.ClassificationStatus;
import com.example.ytclassifier.domain.VideoClassification;
import com.example.ytclassifier.web.projection.VideoCardView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VideoClassificationRepository extends JpaRepository<VideoClassification, String> {

    /**
     * 특정 상태의 영상 카드 목록을 게시일 내림차순으로 조회(영상 메타 join, projection).
     */
    @Query("""
            select v.videoId as videoId, v.title as title, v.description as description,
                   v.thumbnailUrl as thumbnailUrl, v.channelTitle as channelTitle,
                   v.publishedAt as publishedAt, c.status as status
            from VideoClassification c
            join c.video v
            where c.status = :status
            order by v.publishedAt desc
            """)
    List<VideoCardView> findCardsByStatus(@Param("status") ClassificationStatus status);

    /** 상태별 건수(탭 뱃지). */
    long countByStatus(ClassificationStatus status);
}
