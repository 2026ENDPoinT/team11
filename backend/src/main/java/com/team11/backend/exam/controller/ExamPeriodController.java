package com.team11.backend.exam.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.exam.domain.ExamPeriodStatus;
import com.team11.backend.exam.dto.CreateExamPeriodRequest;
import com.team11.backend.exam.dto.ExamPeriodListResponse;
import com.team11.backend.exam.dto.ExamPeriodView;
import com.team11.backend.exam.dto.UpdateExamPeriodRequest;
import com.team11.backend.exam.service.ExamPeriodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EXAM-02~06. EXAM-01(자동 감지)은 사용자가 시험기간을 직접 등록하는 방식으로 가기로 해서 안 만듦.
 * EXAM-07(AI 플랜 생성)은 AI 서버 연동 전이라 아직 없음.
 */
@RestController
@RequestMapping("/api/v1/exam-periods")
public class ExamPeriodController {

    private final ExamPeriodService examPeriodService;

    public ExamPeriodController(ExamPeriodService examPeriodService) {
        this.examPeriodService = examPeriodService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExamPeriodView>> createExamPeriod(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateExamPeriodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(examPeriodService.createExamPeriod(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ExamPeriodListResponse>> getExamPeriods(
            @AuthenticationPrincipal Long userId, @RequestParam(required = false) ExamPeriodStatus status) {
        return ResponseEntity.ok(ApiResponse.success(examPeriodService.getExamPeriods(userId, status)));
    }

    @GetMapping("/{examPeriodId}")
    public ResponseEntity<ApiResponse<ExamPeriodView>> getExamPeriod(
            @AuthenticationPrincipal Long userId, @PathVariable Long examPeriodId) {
        return ResponseEntity.ok(ApiResponse.success(examPeriodService.getExamPeriod(userId, examPeriodId)));
    }

    @PatchMapping("/{examPeriodId}")
    public ResponseEntity<ApiResponse<ExamPeriodView>> updateExamPeriod(
            @AuthenticationPrincipal Long userId, @PathVariable Long examPeriodId,
            @Valid @RequestBody UpdateExamPeriodRequest request) {
        return ResponseEntity.ok(ApiResponse.success(examPeriodService.updateExamPeriod(userId, examPeriodId, request)));
    }

    @DeleteMapping("/{examPeriodId}")
    public ResponseEntity<Void> deleteExamPeriod(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long examPeriodId) {
        examPeriodService.deleteExamPeriod(userId, examPeriodId);
        return ResponseEntity.noContent().build();
    }
}
