package com.team11.backend.home.dto;

import java.time.LocalDate;

public record StreakView(int currentCount, int bestCount, LocalDate lastAchievedDate, boolean todayAchieved,
                          boolean atRisk) {
}
