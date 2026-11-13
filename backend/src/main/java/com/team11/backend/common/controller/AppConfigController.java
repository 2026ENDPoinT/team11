package com.team11.backend.common.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.common.dto.AppConfigResponse;
import com.team11.backend.common.dto.AppConfigResponse.AppVersion;
import com.team11.backend.common.dto.AppConfigResponse.FeatureFlags;
import com.team11.backend.common.dto.AppConfigResponse.Maintenance;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppConfigController {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final int ABSOLUTE_MIN_SLEEP_MIN = 240;
    private static final String CURRENT_APP_VERSION = "1.0.0";

    @GetMapping("/api/v1/app/config")
    public ResponseEntity<ApiResponse<AppConfigResponse>> getConfig() {
        AppVersion version = new AppVersion(CURRENT_APP_VERSION, CURRENT_APP_VERSION);
        AppConfigResponse response = new AppConfigResponse(
                OffsetDateTime.now(ASIA_SEOUL), version, version,
                new Maintenance(false, null, null),
                new FeatureFlags(false, false, true, false, false),
                ABSOLUTE_MIN_SLEEP_MIN);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
