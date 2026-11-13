package com.team11.backend.reports.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.common.Kst;
import com.team11.backend.reports.dto.ReportPeriod;
import com.team11.backend.reports.dto.ReportResponse;
import com.team11.backend.reports.service.ReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** date 생략 시 오늘(KST)이 속한 주/달. */
    @GetMapping
    public ApiResponse<ReportResponse> report(@AuthenticationPrincipal Long userId,
                                              @RequestParam(defaultValue = "WEEK") ReportPeriod period,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(reportService.report(userId, period, date != null ? date : Kst.today()));
    }
}
