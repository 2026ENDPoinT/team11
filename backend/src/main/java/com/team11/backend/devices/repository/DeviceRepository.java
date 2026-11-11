package com.team11.backend.devices.repository;

import com.team11.backend.devices.domain.Device;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByPushToken(String pushToken);

    List<Device> findByUserIdAndActiveTrueOrderByLastActiveAtDesc(Long userId);

    List<Device> findByUserIdAndActiveTrueOrderByLastActiveAtAsc(Long userId);
}
