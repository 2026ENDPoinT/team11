package com.team11.backend.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record SleepDebtResponse(String range, int sleepNeedMin, int currentDebtMin, int missingDays,
                                 List<DailyDebt> daily, Recovery recovery, boolean insufficientData) {

    public record DailyDebt(LocalDate date, int sleptMin, int needMin, int deltaMin, int cumulativeDebtMin) {
    }

    public record Recovery(String suggestion, int napRecommendedMin) {
    }
}
