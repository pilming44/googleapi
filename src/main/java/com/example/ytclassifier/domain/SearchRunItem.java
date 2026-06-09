package com.example.ytclassifier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 한 번의 검색 실행에서 수집된 영상 1건(실행당 영상당 1행). 검색 이력과 영상을 잇는 정규화 테이블.
 * (search_run_id, video_id) 유니크로 실행 내 중복을 DB 차원에서도 방지한다.
 */
@Entity
@Table(name = "search_run_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_run_video", columnNames = {"search_run_id", "video_id"}))
public class SearchRunItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "search_run_id")
    private SearchRun searchRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id")
    private YoutubeVideo video;

    @Column(name = "search_rank")
    private Integer searchRank;

    @Column(name = "page_no")
    private Integer pageNo;

    protected SearchRunItem() {
    }

    public SearchRunItem(SearchRun searchRun, YoutubeVideo video, Integer searchRank, Integer pageNo) {
        this.searchRun = searchRun;
        this.video = video;
        this.searchRank = searchRank;
        this.pageNo = pageNo;
    }

    public Long getId() {
        return id;
    }

    public SearchRun getSearchRun() {
        return searchRun;
    }

    public YoutubeVideo getVideo() {
        return video;
    }

    public Integer getSearchRank() {
        return searchRank;
    }

    public Integer getPageNo() {
        return pageNo;
    }
}
