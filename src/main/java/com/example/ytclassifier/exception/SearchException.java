package com.example.ytclassifier.exception;

/**
 * 검색 실행 중 사용자에게 보여줄 수 있는 오류. (키워드/Key 누락, YouTube API 오류 등)
 */
public class SearchException extends RuntimeException {

    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
