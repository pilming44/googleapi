package com.example.ytclassifier.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * YouTube Data API 호출용 RestClient. baseUrl만 고정하고, 쿼리 파라미터는 호출부에서 조립한다.
 */
@Configuration
public class RestClientConfig {

    public static final String YOUTUBE_API_BASE_URL = "https://www.googleapis.com/youtube/v3";

    @Bean
    public RestClient youtubeRestClient(RestClient.Builder builder) {
        return builder.baseUrl(YOUTUBE_API_BASE_URL).build();
    }
}
