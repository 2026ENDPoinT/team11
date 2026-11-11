package com.team11.backend.logs.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.logs.dto.CreatePrepLogRequest;
import com.team11.backend.logs.dto.CreatePrepLogResponse;
import com.team11.backend.logs.dto.PrepLogListResponse;
import com.team11.backend.logs.service.PrepLogService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prep-logs")
public class PrepLogController {

    private final PrepLogService prepLogService;

    public PrepLogController(PrepLogService prepLogService) {
        this.prepLogService = prepLogService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreatePrepLogResponse>> createOrReplace(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody CreatePrepLogRequest request) {
        var result = prepLogService.createOrReplace(userId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result.response()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PrepLogListResponse>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(prepLogService.list(userId, from, to)));
    }
}
