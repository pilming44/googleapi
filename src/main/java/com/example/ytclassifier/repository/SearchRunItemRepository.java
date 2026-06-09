package com.example.ytclassifier.repository;

import com.example.ytclassifier.domain.SearchRunItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SearchRunItemRepository extends JpaRepository<SearchRunItem, Long> {

    /**
     * 특정 검색 실행에서 수집한 영상 중 가장 오래된 게시일.
     * "이어서 수집"의 재개 지점(publishedBefore)으로 사용한다. 없으면 null.
     */
    @Query("""
            select min(i.video.publishedAt)
            from SearchRunItem i
            where i.searchRun.id = :searchRunId
            """)
    Instant findOldestCollectedPublishedAtForSearchRun(@Param("searchRunId") Long searchRunId);

    /**
     * 같은 검색 조건 전체 이력에서 가장 오래된 게시일.
     * 다음 수집은 이 날짜의 다음 날 00:00:00을 publishedBefore로 잡아 하루 전체를 겹쳐 조회한다.
     */
    @Query("""
            select min(i.video.publishedAt)
            from SearchRunItem i
            where i.searchRun.query = :query
              and i.searchRun.videoDuration = :videoDuration
              and i.searchRun.orderType = :orderType
              and i.video.publishedAt is not null
            """)
    Instant findOldestCollectedPublishedAtForSearchCondition(@Param("query") String query,
                                                             @Param("videoDuration") String videoDuration,
                                                             @Param("orderType") String orderType);
}
