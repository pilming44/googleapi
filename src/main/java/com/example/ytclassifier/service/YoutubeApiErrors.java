package com.example.ytclassifier.service;

/**
 * YouTube Data API 오류 분류 유틸. 403 응답 본문의 reason으로 쿼터 소진과 일시적 호출 제한을 구분한다.
 * (정밀 JSON 파싱 대신 reason 키워드 포함 여부로 판별 — 견고하고 단순.)
 */
public final class YoutubeApiErrors {

    private YoutubeApiErrors() {
    }

    /** 일시적 호출 제한(재시도하면 회복 가능): 429, 또는 403 + rateLimitExceeded/userRateLimitExceeded. */
    public static boolean isRateLimit(int status, String body) {
        if (status == 429) {
            return true;
        }
        if (status == 403 && body != null) {
            return body.contains("rateLimitExceeded") || body.contains("userRateLimitExceeded");
        }
        return false;
    }

    /** 일일 쿼터 소진(오늘은 재시도해도 회복 불가): 403 + quotaExceeded/dailyLimitExceeded. */
    public static boolean isQuotaExceeded(int status, String body) {
        return status == 403 && body != null
                && (body.contains("quotaExceeded") || body.contains("dailyLimitExceeded"));
    }
}
