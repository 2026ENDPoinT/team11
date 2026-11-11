package com.team11.backend.devices.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.devices.domain.Device;
import com.team11.backend.devices.dto.DeviceListResponse;
import com.team11.backend.devices.dto.DeviceView;
import com.team11.backend.devices.dto.RegisterDeviceRequest;
import com.team11.backend.devices.dto.RegisterDeviceResponse;
import com.team11.backend.devices.repository.DeviceRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeviceService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_ACTIVE_DEVICES = 5;

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public record RegisterResult(RegisterDeviceResponse response, boolean created) {
    }

    public RegisterResult register(Long userId, RegisterDeviceRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ASIA_SEOUL);
        Device device = deviceRepository.findByPushToken(request.pushToken()).orElse(null);
        boolean created = device == null;
        if (created) {
            device = deviceRepository.save(new Device(userId, request.platform(), request.pushToken(),
                    request.appVersion(), request.timezone(), now));
        } else {
            device.reassignTo(userId, request.platform(), request.appVersion(), request.timezone(), now);
        }

        enforceDeviceLimit(userId);
        return new RegisterResult(RegisterDeviceResponse.from(device), created);
    }

    @Transactional(readOnly = true)
    public DeviceListResponse list(Long userId) {
        List<DeviceView> devices = deviceRepository.findByUserIdAndActiveTrueOrderByLastActiveAtDesc(userId).stream()
                .map(DeviceView::from)
                .toList();
        return new DeviceListResponse(devices);
    }

    public void delete(Long userId, Long deviceId) {
        deviceRepository.delete(findOwned(userId, deviceId));
    }

    /** 로그아웃 시 호출 — 본인 기기가 아니거나 없으면 조용히 무시(로그아웃 자체는 항상 성공해야 함). */
    public void deactivateIfOwned(Long userId, Long deviceId) {
        deviceRepository.findById(deviceId)
                .filter(device -> device.isOwnedBy(userId))
                .ifPresent(Device::deactivate);
    }

    private void enforceDeviceLimit(Long userId) {
        List<Device> active = deviceRepository.findByUserIdAndActiveTrueOrderByLastActiveAtAsc(userId);
        int excess = active.size() - MAX_ACTIVE_DEVICES;
        for (int i = 0; i < excess; i++) {
            active.get(i).deactivate();
        }
    }

    private Device findOwned(Long userId, Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_FOUND));
        if (!device.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return device;
    }
}
