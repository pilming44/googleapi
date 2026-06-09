package com.example.ytclassifier.web.dto;

import java.util.Arrays;
import java.util.List;

/**
 * 검색 폼(요구사항 2). 키워드는 콤마 또는 줄바꿈으로 여러 개 입력한다.
 * 숫자 항목은 Integer(널 허용)로 받아 바인딩 실패를 막고, 범위는 서비스에서 클램프한다.
 *
 * <p>pageCount 대신 targetCount(목표 수집 건수)를 사용한다. 서비스는 date 정렬 시
 * 페이지네이션 + 기간(publishedBefore) 자동 확장으로 목표 건수를 채울 때까지 수집한다.
 */
public class SearchForm {

    /** 직전 검색 입력값을 세션에 보관할 때 쓰는 키(검색 후에도 폼이 초기화되지 않도록). */
    public static final String SESSION_KEY = "searchForm";

    private String keywords;
    private String priorityChannels;
    private String apiKey;
    private Integer maxResults = 50;      // 페이지당 개수(1~50)
    private Integer targetCount = 50;     // 목표 수집(신규) 건수
    private String order = "date";
    private String videoDuration = "any"; // any | short(<4분) | medium(4~20분) | long(>20분)
    private String publishedAfter;        // yyyy-MM-dd (선택, 하한)
    private String publishedBefore;       // yyyy-MM-dd (선택, 초기 상한)
    /** 콤마/줄바꿈으로 구분된 키워드를 trim·빈값 제거하여 반환한다. */
    public List<String> keywordList() {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split("[,\\r\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 콤마/줄바꿈으로 구분된 우선 수집 채널을 trim·빈값 제거하여 반환한다. */
    public List<String> priorityChannelList() {
        if (priorityChannels == null || priorityChannels.isBlank()) {
            return List.of();
        }
        return Arrays.stream(priorityChannels.split("[,\\r\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getPriorityChannels() {
        return priorityChannels;
    }

    public void setPriorityChannels(String priorityChannels) {
        this.priorityChannels = priorityChannels;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public Integer getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount) {
        this.targetCount = targetCount;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public String getVideoDuration() {
        return videoDuration;
    }

    public void setVideoDuration(String videoDuration) {
        this.videoDuration = videoDuration;
    }

    public String getPublishedAfter() {
        return publishedAfter;
    }

    public void setPublishedAfter(String publishedAfter) {
        this.publishedAfter = publishedAfter;
    }

    public String getPublishedBefore() {
        return publishedBefore;
    }

    public void setPublishedBefore(String publishedBefore) {
        this.publishedBefore = publishedBefore;
    }
}
