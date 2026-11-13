package com.team11.backend.plans.domain;

import java.time.OffsetDateTime;
import java.util.List;

/** 플래너(AI 매핑·규칙)가 만든 결과. 저장 전 중간 표현. generatedBy: AI | RULE */
public record PlanDraft(PlanStatus status, OffsetDateTime bedAt, OffsetDateTime wakeAt, Integer sleepMin,
                        String basisText, String generatedBy, String modelVersion, List<String> reasons,
                        List<String> warnings) {

    public static final String AI = "AI";
    public static final String RULE = "RULE";

    public static PlanDraft infeasible(String basisText, String generatedBy, String modelVersion, List<String> warnings) {
        return new PlanDraft(PlanStatus.NO_FEASIBLE_PLAN, null, null, null, basisText, generatedBy, modelVersion,
                List.of(), warnings);
    }
}
