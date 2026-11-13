package com.team11.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** base-url이 비어 있으면 AI를 호출하지 않는다(NORMAL은 규칙 폴백, FOCUS/EXAM은 503). 타임아웃은 모드별 계산 예산이 달라 셋으로 나눔. */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(String baseUrl, String internalKey, Timeout timeout) {

    public record Timeout(long tonightSeconds, long examSeconds, long focusSeconds) {
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
