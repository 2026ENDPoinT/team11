package com.team11.backend.devices.dto;

import com.team11.backend.devices.domain.Platform;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotNull Platform platform,
        @NotNull @Size(min = 1, max = 255) String pushToken,
        String appVersion,
        String timezone
) {
}
