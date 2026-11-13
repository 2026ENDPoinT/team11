package com.team11.backend.logs.dto;

import com.team11.backend.logs.domain.SleepLog;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SleepLogView(Long sleepLogId, LocalDate date, OffsetDateTime bedAt, OffsetDateTime wakeAt, int durationMin,
                           Integer quality, String memo) {

    public static SleepLogView from(SleepLog l) {
        return new SleepLogView(l.getId(), l.getDate(), l.getBedAt(), l.getWakeAt(), l.getDurationMin(), l.getQuality(),
                l.getMemo());
    }
}
