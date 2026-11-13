package com.team11.backend.plans.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.plans.domain.PlanMode;
import com.team11.backend.plans.dto.GeneratePlanRequest;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.plans.service.PlanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /** 저장된 플랜이 있으면 반환, 없으면 생성 후 저장. 시험 모드는 처음엔 POST로 공부 모드 입력을 보내야 한다. */
    @GetMapping("/today")
    public ApiResponse<PlanView> getToday(@AuthenticationPrincipal Long userId,
                                          @RequestParam(defaultValue = "NORMAL") PlanMode mode) {
        return ApiResponse.success(planService.getToday(userId, mode));
    }

    /** 다시 만들기(덮어쓰기). 시험 모드는 body에 preparationState·examNature 필수. */
    @PostMapping("/today")
    public ApiResponse<PlanView> regenerateToday(@AuthenticationPrincipal Long userId,
                                                 @RequestParam(defaultValue = "NORMAL") PlanMode mode,
                                                 @RequestBody(required = false) GeneratePlanRequest request) {
        return ApiResponse.success(planService.regenerateToday(userId, mode, request));
    }
}
