package com.team11.backend.devices.dto;

import com.team11.backend.devices.domain.Device;
import com.team11.backend.devices.domain.Platform;

public record RegisterDeviceResponse(Long deviceId, Platform platform) {

    public static RegisterDeviceResponse from(Device device) {
        return new RegisterDeviceResponse(device.getId(), device.getPlatform());
    }
}
