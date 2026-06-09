package com.example.ytclassifier.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * YouTube Data API v3 search.list 응답(필요한 필드만). 알 수 없는 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeSearchResponse(
        String nextPageToken,
        PageInfo pageInfo,
        List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageInfo(Integer totalResults, Integer resultsPerPage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Id id, Snippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Id(String videoId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(
            String title,
            String description,
            String channelId,
            String channelTitle,
            String publishedAt,
            Thumbnails thumbnails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnails(
            // 'default' 는 자바 예약어이므로 매핑 이름을 명시한다.
            @JsonProperty("default") Thumb defaultThumb,
            Thumb medium,
            Thumb high) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumb(String url, Integer width, Integer height) {
    }
}
