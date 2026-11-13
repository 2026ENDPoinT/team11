package com.team11.backend.plans.dto;

import com.team11.backend.plans.domain.Plan;
import com.team11.backend.plans.domain.PlanMode;
import com.team11.backend.plans.domain.PlanStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 홈 카드. basisText = 기상일 첫 일정("09:00 오픈소스소프트웨어"), 없으면 null. sleepMin = 예상 실수면(분). */
public record PlanView(Long planId, LocalDate date, PlanMode mode, PlanStatus status, OffsetDateTime bedAt,
                       OffsetDateTime wakeAt, Integer sleepMin, String basisText, String generatedBy,
                       String modelVersion, List<String> reasons, List<String> warnings) {

    public static PlanView from(Plan p) {
        return new PlanView(p.getId(), p.getDate(), p.getMode(), p.getStatus(), p.getBedAt(), p.getWakeAt(),
                p.getSleepMin(), p.getBasisText(), p.getGeneratedBy(), p.getModelVersion(), p.getReasons(),
                p.getWarnings());
    }
}
