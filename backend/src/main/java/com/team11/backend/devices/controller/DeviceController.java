package com.team11.backend.devices.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.devices.dto.DeviceListResponse;
import com.team11.backend.devices.dto.RegisterDeviceRequest;
import com.team11.backend.devices.dto.RegisterDeviceResponse;
import com.team11.backend.devices.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegisterDeviceResponse>> register(@AuthenticationPrincipal Long userId,
                                                                          @Valid @RequestBody RegisterDeviceRequest request) {
        var result = deviceService.register(userId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result.response()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DeviceListResponse>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.list(userId)));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long deviceId) {
        deviceService.delete(userId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
