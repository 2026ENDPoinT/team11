package com.team11.backend.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiPlanTonightResult(Result result, String modelVersion) {

    public record Result(OffsetDateTime recommendedBedAt, OffsetDateTime recommendedWakeAt, int sleepMin,
                          List<String> reasons, List<String> warnings) {
    }
}
