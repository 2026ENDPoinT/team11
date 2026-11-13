package com.team11.backend.common.controller;

import com.team11.backend.ai.AiProperties;
import com.team11.backend.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 앱 시작 시 한 번 읽는 설정. 인증 없음. */
@RestController
@RequestMapping("/api/v1/app")
public class AppConfigController {

    private final AiProperties aiProperties;

    public AppConfigController(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.success(Map.of(
                "apiVersion", "v1",
                // AI 서버가 설정되지 않으면 집중·시험 모드는 503이 나므로 프론트가 버튼을 숨길 수 있게 알려준다.
                "featureFlags", Map.of(
                        "focusMode", aiProperties.isConfigured(),
                        "examMode", aiProperties.isConfigured(),
                        "everytimeImport", true)));
    }
}
