package com.team11.backend.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record WeeklyStatsResponse(
        LocalDate weekStart, int loggedDays, Integer avgSleepMin, String avgBedTime, String avgWakeTime,
        Integer bedTimeStdMin, int goalMetDays, int goalDays, StreakSummary streak, Integer avgPrepMin,
        VsLastWeek vsLastWeek, List<String> highlights, boolean insufficientData
) {

    public record StreakSummary(int currentCount, int bestCount) {
    }

    public record VsLastWeek(Integer avgSleepMin, Integer bedTimeStdMin, Integer goalMetDays) {
    }
}
