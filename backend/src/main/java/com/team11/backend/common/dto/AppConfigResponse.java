package com.team11.backend.common.dto;

import java.time.OffsetDateTime;

/**
 * ETC-01 GET /app/config. featureFlags는 전부 2차 기능(시험기간/eclass/웨어러블/자연어 비서/팁)이라 현재는 전부 false —
 * 해당 도메인을 실제로 만들 때 값을 바꾸면 됨. minAppVersion/latestAppVersion은 아직 배포된 앱이 없어 고정값.
 */
public record AppConfigResponse(
        OffsetDateTime serverTime, AppVersion minAppVersion, AppVersion latestAppVersion, Maintenance maintenance,
        FeatureFlags featureFlags, int minSleepMin
) {

    public record AppVersion(String ios, String android) {
    }

    public record Maintenance(boolean active, String message, OffsetDateTime until) {
    }

    public record FeatureFlags(boolean examMode, boolean eclassSync, boolean wearableSync, boolean assistant,
                                boolean tips) {
    }
}
