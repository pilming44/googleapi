package com.example.ytclassifier.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * YouTube 영상 메타데이터. PK(video_id)는 YouTube가 발급하는 값이므로 직접 할당한다(생성 전략 없음).
 * 분류 상태는 {@link VideoClassification} 으로 분리한다(요구사항 11: 이력/상태 분리, 정규화).
 *
 * <p>PK를 직접 할당하므로 Spring Data가 save() 시 merge(SELECT 후 INSERT) 대신 persist를 쓰도록
 * {@link Persistable} 을 구현한다. 이는 @MapsId 자식(VideoClassification) cascade 저장도 안정적으로 만든다.
 */
@Entity
@Table(name = "youtube_video")
public class YoutubeVideo implements Persistable<String> {

    @Transient
    private boolean isNew = true;

    @Id
    @Column(name = "video_id", nullable = false, length = 32)
    private String videoId;

    @Column(nullable = false, length = 300)
    private String title;

    // search.list의 description은 스니펫(짧음)이지만 여유 있게. (MySQL/PostgreSQL 이식 시 그대로 VARCHAR)
    @Column(length = 5000)
    private String description;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "channel_id", length = 64)
    private String channelId;

    @Column(name = "channel_title", length = 200)
    private String channelTitle;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToOne(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private VideoClassification classification;

    protected YoutubeVideo() {
    }

    public YoutubeVideo(String videoId) {
        this.videoId = videoId;
    }

    @Override
    public String getId() {
        return videoId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    /** 분류 연관을 양방향으로 일관되게 설정한다. */
    public void setClassification(VideoClassification classification) {
        this.classification = classification;
        if (classification != null) {
            classification.setVideo(this);
        }
    }

    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public void setChannelTitle(String channelTitle) {
        this.channelTitle = channelTitle;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public VideoClassification getClassification() {
        return classification;
    }
}
