package com.team11.backend.notifications.domain;

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
@Table(name = "notifications")
@Getter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String title;
    private String body;

    /** 타입별로 모양이 다른 payload라 그냥 원문 JSON 문자열로 저장(컬럼은 TEXT), 응답 시 ObjectMapper로 파싱. */
    private String data;

    private OffsetDateTime readAt;

    @CreationTimestamp
    private Instant createdAt;

    public Notification(Long userId, NotificationType type, String title, String body, String data) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.data = data;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void markRead(OffsetDateTime now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }
}
