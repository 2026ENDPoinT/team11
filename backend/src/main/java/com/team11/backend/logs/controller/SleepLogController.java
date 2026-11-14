package com.team11.backend.logs.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.logs.dto.SleepLogImportRequest;
import com.team11.backend.logs.dto.SleepLogImportResponse;
import com.team11.backend.logs.dto.SleepLogListResponse;
import com.team11.backend.logs.dto.SleepLogRequest;
import com.team11.backend.logs.dto.SleepLogView;
import com.team11.backend.logs.service.SleepLogService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sleep-logs")
public class SleepLogController {

    private final SleepLogService sleepLogService;

    public SleepLogController(SleepLogService sleepLogService) {
        this.sleepLogService = sleepLogService;
    }

    @GetMapping
    public ApiResponse<SleepLogListResponse> list(@AuthenticationPrincipal Long userId,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(sleepLogService.list(userId, from, to));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SleepLogView>> create(@AuthenticationPrincipal Long userId,
                                                             @Valid @RequestBody SleepLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(sleepLogService.create(userId, request)));
    }

    @PostMapping("/import")
    public ApiResponse<SleepLogImportResponse> importFromHealthKit(@AuthenticationPrincipal Long userId,
                                                                   @Valid @RequestBody SleepLogImportRequest request) {
        return ApiResponse.success(sleepLogService.importNights(userId, request));
    }

    @GetMapping("/{sleepLogId}")
    public ApiResponse<SleepLogView> get(@AuthenticationPrincipal Long userId, @PathVariable Long sleepLogId) {
        return ApiResponse.success(sleepLogService.get(userId, sleepLogId));
    }

    @PutMapping("/{sleepLogId}")
    public ApiResponse<SleepLogView> update(@AuthenticationPrincipal Long userId, @PathVariable Long sleepLogId,
                                            @Valid @RequestBody SleepLogRequest request) {
        return ApiResponse.success(sleepLogService.update(userId, sleepLogId, request));
    }

    @DeleteMapping("/{sleepLogId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long sleepLogId) {
        sleepLogService.delete(userId, sleepLogId);
        return ResponseEntity.noContent().build();
    }
}
