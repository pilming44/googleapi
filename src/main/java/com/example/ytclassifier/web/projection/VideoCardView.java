package com.example.ytclassifier.web.projection;

import com.example.ytclassifier.domain.ClassificationStatus;

import java.time.Instant;

/**
 * 카드 목록 렌더링에 필요한 스칼라만 담는 인터페이스 projection.
 * 엔티티 대신 이 projection을 쓰면 join 한 번으로 조회되어 N+1과 LazyInitialization을 피한다.
 */
public interface VideoCardView {

    String getVideoId();

    String getTitle();

    String getDescription();

    String getThumbnailUrl();

    String getChannelTitle();

    Instant getPublishedAt();

    ClassificationStatus getStatus();
}
