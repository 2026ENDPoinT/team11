package com.team11.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** AI-06 `plan/focus-sleep` 요청(FOCUS_SLEEP_V1). wakeDeadlineAt은 준비·통학·여유를 Spring이 미리 뺀 최종 기상 마감. */
public record AiFocusSleepRequest(
        String contractVersion, OffsetDateTime asOfAt, String timezone, OffsetDateTime wakeDeadlineAt,
        int minInBedMinutes, int maxInBedMinutes, List<History> history
) {

    public static final String CONTRACT_VERSION = "FOCUS_SLEEP_V1";
    public static final String TIMEZONE = "Asia/Seoul";

    /** 기상 날짜당 1건. bedtime/wakeTime은 "HH:mm". */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record History(LocalDate wakeDate, String bedtime, String wakeTime, int asleepMinutes,
                           Integer latencyMinutes) {
    }
}
