package com.team11.backend.logs.dto;

import com.team11.backend.logs.domain.SleepLog;
import com.team11.backend.logs.domain.SleepLogSource;
import com.team11.backend.logs.domain.SleepLogStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SleepLogView(
        Long sleepLogId, LocalDate date, OffsetDateTime bedAt, OffsetDateTime wakeAt, Integer durationMin,
        Integer quality, SleepLogStatus status, SleepLogSource source
) {

    public static SleepLogView from(SleepLog log) {
        return new SleepLogView(log.getId(), log.getDate(), log.getBedAt(), log.getWakeAt(), log.getDurationMin(),
                log.getQuality(), log.getStatus(), log.getSource());
    }
}
