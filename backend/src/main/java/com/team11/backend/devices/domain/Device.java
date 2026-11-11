package com.team11.backend.devices.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private Platform platform;

    private String pushToken;
    private String appVersion;
    private String timezone;
    private boolean active;
    private OffsetDateTime lastActiveAt;

    @CreationTimestamp
    private Instant createdAt;

    public Device(Long userId, Platform platform, String pushToken, String appVersion, String timezone,
                  OffsetDateTime now) {
        this.userId = userId;
        this.platform = platform;
        this.pushToken = pushToken;
        this.appVersion = appVersion;
        this.timezone = timezone != null ? timezone : "Asia/Seoul";
        this.active = true;
        this.lastActiveAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 다른 사용자가 쓰던 토큰이 이 사용자 걸로 넘어올 때도 포함(기기 공유 시 알림 오발송 방지). */
    public void reassignTo(Long userId, Platform platform, String appVersion, String timezone, OffsetDateTime now) {
        this.userId = userId;
        this.platform = platform;
        this.appVersion = appVersion;
        this.timezone = timezone != null ? timezone : "Asia/Seoul";
        this.active = true;
        this.lastActiveAt = now;
    }

    public void deactivate() {
        this.active = false;
    }
}
