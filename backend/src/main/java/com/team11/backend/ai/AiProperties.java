package com.team11.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 서버 호스팅 위치가 아직 미정(CLAUDE.md §10)이라 base-url은 기본 빈 문자열 — 비어있으면 호출 자체를 안 하고 폴백한다. */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(String baseUrl, String internalKey, long timeoutSeconds) {

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
