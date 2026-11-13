package com.team11.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** AI-06 응답. 원본 모델 출력이 snake_case라 필드명을 명시한다. */
public record AiFocusSleepResult(Result result, String modelVersion) {

    public record Result(
            @JsonProperty("prediction_used_for_ranking") Boolean predictionUsedForRanking,
            @JsonProperty("cycle_fallback") CycleFallback cycleFallback,
            Recommendation recommendation
    ) {
    }

    public record CycleFallback(boolean used, String note) {
    }

    public record Recommendation(
            OffsetDateTime bedtime,
            @JsonProperty("wake_time") OffsetDateTime wakeTime,
            @JsonProperty("sleep_opportunity_hours") double sleepOpportunityHours,
            @JsonProperty("estimated_actual_sleep_hours") double estimatedActualSleepHours,
            @JsonProperty("predicted_pvt_lapse_score") Double predictedPvtLapseScore
    ) {
    }
}
