package com.team11.backend.logs.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.logs.domain.SleepLogStatus;
import com.team11.backend.logs.dto.CreateSleepLogRequest;
import com.team11.backend.logs.dto.EndSleepLogRequest;
import com.team11.backend.logs.dto.EndSleepLogResponse;
import com.team11.backend.logs.dto.SleepLogListResponse;
import com.team11.backend.logs.dto.SleepLogView;
import com.team11.backend.logs.dto.StartSleepLogRequest;
import com.team11.backend.logs.dto.UpdateSleepLogRequest;
import com.team11.backend.logs.service.SleepLogService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/v1/sleep-logs")
public class SleepLogController {

    private final SleepLogService sleepLogService;

    public SleepLogController(SleepLogService sleepLogService) {
        this.sleepLogService = sleepLogService;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<SleepLogView>> start(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody(required = false) StartSleepLogRequest request) {
        StartSleepLogRequest body = request != null ? request : new StartSleepLogRequest(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(sleepLogService.start(userId, body)));
    }

    @PostMapping("/end")
    public ResponseEntity<ApiResponse<EndSleepLogResponse>> end(@AuthenticationPrincipal Long userId,
                                                                  @Valid @RequestBody(required = false) EndSleepLogRequest request) {
        EndSleepLogRequest body = request != null ? request : new EndSleepLogRequest(null, null);
        return ResponseEntity.ok(ApiResponse.success(sleepLogService.end(userId, body)));
    }

    @GetMapping("/open")
    public ResponseEntity<ApiResponse<SleepLogView>> getOpen(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(sleepLogService.getOpen(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SleepLogView>> create(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody CreateSleepLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(sleepLogService.create(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SleepLogListResponse>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SleepLogStatus status) {
        return ResponseEntity.ok(ApiResponse.success(sleepLogService.list(userId, from, to, status)));
    }

    @GetMapping("/{sleepLogId}")
    public ResponseEntity<ApiResponse<SleepLogView>> getOne(@AuthenticationPrincipal Long userId,
                                                              @PathVariable Long sleepLogId) {
        return ResponseEntity.ok(ApiResponse.success(sleepLogService.getOne(userId, sleepLogId)));
    }

    @PatchMapping("/{sleepLogId}")
    public ResponseEntity<ApiResponse<SleepLogView>> update(@AuthenticationPrincipal Long userId,
                                                              @PathVariable Long sleepLogId,
                                                              @Valid @RequestBody UpdateSleepLogRequest request) {
        return ResponseEntity.ok(ApiResponse.success(sleepLogService.update(userId, sleepLogId, request)));
    }

    @DeleteMapping("/{sleepLogId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long sleepLogId) {
        sleepLogService.delete(userId, sleepLogId);
        return ResponseEntity.noContent().build();
    }
}
