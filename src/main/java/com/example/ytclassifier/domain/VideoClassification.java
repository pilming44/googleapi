package com.example.ytclassifier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 영상 분류 상태. {@link YoutubeVideo} 와 PK(video_id)를 공유하는 1:1 관계(@MapsId).
 * 주의: id 필드를 직접 세팅하지 말고 setVideo(...) 로 연관만 설정한다. id는 @MapsId가 채운다.
 */
@Entity
@Table(name = "video_classification")
public class VideoClassification {

    @Id
    @Column(name = "video_id", length = 32)
    private String videoId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private YoutubeVideo video;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClassificationStatus status;

    @Column(name = "classified_at")
    private Instant classifiedAt;

    @Column(length = 1000)
    private String memo;

    protected VideoClassification() {
    }

    public VideoClassification(YoutubeVideo video, ClassificationStatus status, Instant classifiedAt) {
        this.video = video;
        this.status = status;
        this.classifiedAt = classifiedAt;
    }

    public String getVideoId() {
        return videoId;
    }

    public YoutubeVideo getVideo() {
        return video;
    }

    public void setVideo(YoutubeVideo video) {
        this.video = video;
    }

    public ClassificationStatus getStatus() {
        return status;
    }

    public void setStatus(ClassificationStatus status) {
        this.status = status;
    }

    public Instant getClassifiedAt() {
        return classifiedAt;
    }

    public void setClassifiedAt(Instant classifiedAt) {
        this.classifiedAt = classifiedAt;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
