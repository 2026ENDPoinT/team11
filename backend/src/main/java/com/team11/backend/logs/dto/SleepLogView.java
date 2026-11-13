package com.team11.backend.logs.dto;

import com.team11.backend.logs.domain.SleepLog;
import com.team11.backend.logs.domain.SleepLogSource;
import com.team11.backend.logs.domain.SleepLogStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SleepLogView(
        Long sleepLogId, LocalDate date, OffsetDateTime bedAt, OffsetDateTime wakeAt, Integer durationMin,
        Integer quality, SleepLogStatus status, SleepLogSource source, List<SleepStageView> stages
) {

    /** 웨어러블 수면 단계가 없는(=조회하지 않은) 경로용. */
    public static SleepLogView from(SleepLog log) {
        return from(log, List.of());
    }

    public static SleepLogView from(SleepLog log, List<SleepStageView> stages) {
        return new SleepLogView(log.getId(), log.getDate(), log.getBedAt(), log.getWakeAt(), log.getDurationMin(),
                log.getQuality(), log.getStatus(), log.getSource(), stages);
    }
}
