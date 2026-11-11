package com.team11.backend.devices.dto;

import com.team11.backend.devices.domain.Device;
import com.team11.backend.devices.domain.Platform;
import java.time.OffsetDateTime;

public record DeviceView(Long deviceId, Platform platform, String appVersion, String timezone,
                          OffsetDateTime lastActiveAt) {

    public static DeviceView from(Device device) {
        return new DeviceView(device.getId(), device.getPlatform(), device.getAppVersion(), device.getTimezone(),
                device.getLastActiveAt());
    }
}
