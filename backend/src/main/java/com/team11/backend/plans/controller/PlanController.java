package com.team11.backend.plans.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.plans.service.PlanService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/plans/tonight")
    public ResponseEntity<ApiResponse<PlanView>> getTonightPlan(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(planService.getTonightPlan(userId)));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<PlanView>> getPlanByDate(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlanByDate(userId, date)));
    }
}
