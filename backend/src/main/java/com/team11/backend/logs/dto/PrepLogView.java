package com.team11.backend.logs.dto;

import com.team11.backend.logs.domain.PrepLog;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PrepLogView(Long prepLogId, LocalDate date, OffsetDateTime wakeAt, OffsetDateTime departureAt, int prepMin) {

    public static PrepLogView from(PrepLog log) {
        return new PrepLogView(log.getId(), log.getDate(), log.getWakeAt(), log.getDepartureAt(), log.getPrepMin());
    }
}
