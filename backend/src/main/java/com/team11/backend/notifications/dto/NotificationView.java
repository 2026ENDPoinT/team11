package com.team11.backend.notifications.dto;

import com.team11.backend.notifications.domain.NotificationType;
import java.time.OffsetDateTime;

public record NotificationView(Long notificationId, NotificationType type, String title, String body, Object data,
                                OffsetDateTime readAt, OffsetDateTime createdAt) {
}
