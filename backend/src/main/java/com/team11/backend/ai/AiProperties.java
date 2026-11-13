package com.team11.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 통합 AI 서버 주소, 내부 인증 키, 연결 및 응답 타임아웃. */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(String baseUrl, String internalKey, @DefaultValue("4") long timeoutSeconds,
                           @DefaultValue("15") long longTimeoutSeconds) {

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
