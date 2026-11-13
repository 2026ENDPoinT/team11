package com.team11.backend.users.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 온보딩 0-3~0-5: 동의 + 수면 프로필 + 시험 설정 + 알림 허용을 프론트가 모아 마지막 "다음"에 한 번에 제출한다.
 * 중간 이탈 시 저장되는 것이 없는 게 의도된 동작.
 */
public record OnboardingRequest(
        @NotEmpty(message = "동의 항목이 필요해요.") List<@Valid ConsentItem> consents,
        @NotNull @Min(300) @Max(600) Integer sleepNeedMin,
        @Min(0) @Max(60) Integer sleepLatencyMin,
        @NotNull @Min(0) @Max(240) Integer commuteMin,
        @NotNull @Min(0) @Max(240) Integer prepMin,
        @Min(1) @Max(14) Integer examPrepDays,
        Boolean notificationAllowed
) {
}
