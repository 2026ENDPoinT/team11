package com.team11.backend.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record TrendResponse(String metric, String range, List<Point> points, List<Point> movingAvg7d) {

    public record Point(LocalDate date, int value) {
    }
}
