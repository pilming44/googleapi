package com.example.ytclassifier.domain;

/**
 * 영상 분류 상태. 화면 라벨(한글)을 함께 보관한다.
 */
public enum ClassificationStatus {

    UNCLASSIFIED("미분류"),
    KEEP("보관"),
    HOLD("보류"),
    EXCLUDE("제외");

    private final String label;

    ClassificationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
