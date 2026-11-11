package com.team11.backend.stats.dto;

import com.team11.backend.users.domain.Chronotype;

public record ChronotypeEstimateResponse(Chronotype chronotype, String midSleepFreeday, String confidence,
                                          int basedOnDays, String suggestion, boolean applied,
                                          boolean insufficientData) {
}
