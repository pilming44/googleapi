package com.example.ytclassifier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 검색 실행 이력(요구사항 10). 영상 분류 상태와는 분리되어 있다(요구사항 11).
 * API Key 원문은 절대 저장하지 않는다 — 라벨(apiKeyAlias)만 보관(nullable).
 */
@Entity
@Table(name = "search_run")
public class SearchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String query;

    @Column(name = "api_key_alias", length = 100)
    private String apiKeyAlias;

    @Column(name = "max_results")
    private int maxResults;

    // 이 실행에서 실제로 수행한 search.list 호출(=페이지) 총 횟수. 쿼터 = pageCount * 100유닛.
    @Column(name = "page_count")
    private int pageCount;

    @Column(name = "target_count")
    private int targetCount;

    @Column(name = "order_type", length = 20)
    private String orderType;

    @Column(name = "video_duration", length = 20)
    private String videoDuration;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_fetched_count")
    private int totalFetchedCount;

    @Column(name = "new_video_count")
    private int newVideoCount;

    @Column(name = "duplicated_video_count")
    private int duplicatedVideoCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    // 수집 종료 사유(목표 달성 / 결과 소진 / 안전 한도 도달 등)
    @Column(name = "result_note", length = 300)
    private String resultNote;

    public Long getId() {
        return id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getApiKeyAlias() {
        return apiKeyAlias;
    }

    public void setApiKeyAlias(String apiKeyAlias) {
        this.apiKeyAlias = apiKeyAlias;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getTotalFetchedCount() {
        return totalFetchedCount;
    }

    public void setTotalFetchedCount(int totalFetchedCount) {
        this.totalFetchedCount = totalFetchedCount;
    }

    public int getNewVideoCount() {
        return newVideoCount;
    }

    public void setNewVideoCount(int newVideoCount) {
        this.newVideoCount = newVideoCount;
    }

    public int getDuplicatedVideoCount() {
        return duplicatedVideoCount;
    }

    public void setDuplicatedVideoCount(int duplicatedVideoCount) {
        this.duplicatedVideoCount = duplicatedVideoCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
    }

    public String getVideoDuration() {
        return videoDuration;
    }

    public void setVideoDuration(String videoDuration) {
        this.videoDuration = videoDuration;
    }

    public String getResultNote() {
        return resultNote;
    }

    public void setResultNote(String resultNote) {
        this.resultNote = resultNote;
    }
}
