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
     * 같은 검색어+정렬에서 '특정 길이 버킷'으로 수집한 영상 중 가장 오래된 게시일.
     * 버킷(any/short/medium/long)별로 독립적인 '이어서 수집' 재개 지점(publishedBefore)을 제공한다.
     * 다음 수집은 이 날짜의 다음 날 00:00:00을 publishedBefore로 잡아 하루 전체를 겹쳐 조회한다.
     * (medium·long 두 버킷을 함께 쓰는 '4분 이상' 모드에서, 한 버킷의 진행이 다른 버킷의 재개 지점을
     *  과거로 끌어내려 최신 영상을 누락시키던 문제를 방지한다.)
     */
    @Query("""
            select min(i.video.publishedAt)
            from SearchRunItem i
            where i.searchRun.query = :query
              and i.videoDurationBucket = :bucket
              and i.searchRun.orderType = :orderType
              and i.video.publishedAt is not null
            """)
    Instant findOldestCollectedPublishedAtForBucket(@Param("query") String query,
                                                    @Param("bucket") String bucket,
                                                    @Param("orderType") String orderType);
}
