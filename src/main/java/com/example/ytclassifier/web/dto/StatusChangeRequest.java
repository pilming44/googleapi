package com.example.ytclassifier.web.dto;

import com.example.ytclassifier.domain.ClassificationStatus;

/**
 * 카드 상태 변경 요청(AJAX 본문).
 */
public record StatusChangeRequest(ClassificationStatus status) {
}
