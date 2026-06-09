package com.example.ytclassifier.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * YouTube Data API v3 channels.list 응답(채널 ID와 업로드 플레이리스트만 사용).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeChannelResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, Snippet snippet, ContentDetails contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDetails(RelatedPlaylists relatedPlaylists) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelatedPlaylists(String uploads) {
    }
}
