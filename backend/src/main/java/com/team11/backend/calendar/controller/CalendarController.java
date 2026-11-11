package com.team11.backend.calendar.controller;

import com.team11.backend.calendar.dto.DaySummaryResponse;
import com.team11.backend.calendar.dto.MonthSummaryResponse;
import com.team11.backend.calendar.service.CalendarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.team11.backend.common.ApiResponse;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/month")
    public ResponseEntity<ApiResponse<MonthSummaryResponse>> getMonth(@AuthenticationPrincipal Long userId,
                                                                        @RequestParam String month) {
        return ResponseEntity.ok(ApiResponse.success(calendarService.getMonthSummary(userId, month)));
    }

    @GetMapping("/day")
    public ResponseEntity<ApiResponse<DaySummaryResponse>> getDay(@AuthenticationPrincipal Long userId,
                                                                     @RequestParam String date) {
        return ResponseEntity.ok(ApiResponse.success(calendarService.getDaySummary(userId, date)));
    }
}
