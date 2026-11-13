package com.team11.backend.plans.dto;

import com.team11.backend.plans.domain.PlanFeedbackRating;
import com.team11.backend.plans.domain.PlanFeedbackReason;
import jakarta.validation.constraints.NotNull;

public record PlanFeedbackRequest(
        @NotNull PlanFeedbackRating rating,
        Boolean followed,
        PlanFeedbackReason reason
) {
}
