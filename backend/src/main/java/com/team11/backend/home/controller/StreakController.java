package com.team11.backend.home.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.home.dto.DailyResultsResponse;
import com.team11.backend.home.dto.StreakView;
import com.team11.backend.home.service.StreakService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StreakController {

    private final StreakService streakService;

    public StreakController(StreakService streakService) {
        this.streakService = streakService;
    }

    @GetMapping("/api/v1/streak")
    public ResponseEntity<ApiResponse<StreakView>> getStreak(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(streakService.getStreak(userId)));
    }

    @GetMapping("/api/v1/daily-results")
    public ResponseEntity<ApiResponse<DailyResultsResponse>> getDailyResults(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(streakService.getDailyResults(userId, from, to)));
    }
}
