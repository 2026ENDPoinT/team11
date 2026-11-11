package com.team11.backend.stats.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.stats.dto.ChronotypeEstimateResponse;
import com.team11.backend.stats.dto.SleepDebtResponse;
import com.team11.backend.stats.dto.TrendResponse;
import com.team11.backend.stats.dto.UpcomingRisksResponse;
import com.team11.backend.stats.dto.WeeklyStatsResponse;
import com.team11.backend.stats.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/api/v1/sleep-debt")
    public ResponseEntity<ApiResponse<SleepDebtResponse>> getSleepDebt(@AuthenticationPrincipal Long userId,
                                                                          @RequestParam String range) {
        return ResponseEntity.ok(ApiResponse.success(statsService.getSleepDebt(userId, range)));
    }

    @GetMapping("/api/v1/stats/weekly")
    public ResponseEntity<ApiResponse<WeeklyStatsResponse>> getWeekly(
            @AuthenticationPrincipal Long userId, @RequestParam(required = false) String weekStart) {
        return ResponseEntity.ok(ApiResponse.success(statsService.getWeeklyStats(userId, weekStart)));
    }

    @GetMapping("/api/v1/stats/trend")
    public ResponseEntity<ApiResponse<TrendResponse>> getTrend(@AuthenticationPrincipal Long userId,
                                                                  @RequestParam String range,
                                                                  @RequestParam String metric) {
        return ResponseEntity.ok(ApiResponse.success(statsService.getTrend(userId, range, metric)));
    }

    @GetMapping("/api/v1/insights/upcoming-risks")
    public ResponseEntity<ApiResponse<UpcomingRisksResponse>> getUpcomingRisks(
            @AuthenticationPrincipal Long userId, @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(ApiResponse.success(statsService.getUpcomingRisks(userId, days)));
    }

    @GetMapping("/api/v1/insights/chronotype")
    public ResponseEntity<ApiResponse<ChronotypeEstimateResponse>> getChronotype(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(statsService.getChronotypeEstimate(userId)));
    }
}
