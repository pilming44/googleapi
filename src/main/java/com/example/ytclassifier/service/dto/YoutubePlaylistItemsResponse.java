package com.example.ytclassifier.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * YouTube Data API v3 playlistItems.list 응답(업로드 플레이리스트 수집용).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubePlaylistItemsResponse(
        String nextPageToken,
        PageInfo pageInfo,
        List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageInfo(Integer totalResults, Integer resultsPerPage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Snippet snippet, ContentDetails contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(
            String title,
            String description,
            String channelId,
            String channelTitle,
            String publishedAt,
            ResourceId resourceId,
            YoutubeSearchResponse.Thumbnails thumbnails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceId(String videoId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDetails(String videoId, String videoPublishedAt) {
    }
}
