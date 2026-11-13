package com.team11.backend.plans.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.plans.dto.ExamSleepPlanRequest;
import com.team11.backend.plans.dto.ExamSleepPlanView;
import com.team11.backend.plans.dto.PlanFeedbackRequest;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.plans.service.PlanService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** EXAM-08. AI-03(exam-sleep) 연동 — 시험 1건 기준 1회성 추천, plans에 저장하지 않는다. */
    @PostMapping("/plans/exam-sleep")
    public ResponseEntity<ApiResponse<ExamSleepPlanView>> getExamSleepPlan(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody ExamSleepPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success(planService.generateExamSleepPlan(userId, request)));
    }

    @PostMapping("/plans/{planId}/feedback")
    public ResponseEntity<Void> submitPlanFeedback(@AuthenticationPrincipal Long userId, @PathVariable Long planId,
                                                     @Valid @RequestBody PlanFeedbackRequest request) {
        planService.submitFeedback(userId, planId, request);
        return ResponseEntity.noContent().build();
    }
}
