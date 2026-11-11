package com.team11.backend.notifications.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.notifications.domain.Notification;
import com.team11.backend.notifications.domain.NotificationType;
import com.team11.backend.notifications.dto.NotificationListResponse;
import com.team11.backend.notifications.dto.NotificationView;
import com.team11.backend.notifications.dto.UnreadCountResponse;
import com.team11.backend.notifications.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class NotificationService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, String cursor, Integer size, boolean unreadOnly) {
        int pageSize = size != null ? size : DEFAULT_SIZE;
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Long cursorId = parseCursor(cursor);

        List<Notification> rows = notificationRepository.findPage(userId, cursorId, unreadOnly,
                PageRequest.of(0, pageSize + 1));
        boolean hasNext = rows.size() > pageSize;
        List<Notification> page = hasNext ? rows.subList(0, pageSize) : rows;

        List<NotificationView> items = page.stream().map(this::toView).toList();
        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new NotificationListResponse(items, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        notification.markRead(OffsetDateTime.now(ASIA_SEOUL));
    }

    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, OffsetDateTime.now(ASIA_SEOUL));
    }

    /** 홈 조회 시 스트릭 위험이면 호출 — 오늘(KST) 이미 하나 있으면 건너뛴다(배치 없이 하루 1건 멱등 생성). */
    public void ensureStreakRiskNotification(Long userId, int currentCount) {
        OffsetDateTime todayStart = LocalDate.now(ASIA_SEOUL).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        boolean exists = notificationRepository.existsByUserIdAndTypeAndCreatedAtAfter(userId,
                NotificationType.STREAK_RISK, todayStart.toInstant());
        if (exists) {
            return;
        }
        String data = "{\"currentCount\":%d}".formatted(currentCount);
        notificationRepository.save(new Notification(userId, NotificationType.STREAK_RISK,
                "오늘 스트릭이 끊길 위험이 있어요", "지금까지 %d일 연속 달성했어요. 오늘도 이어가 보세요.".formatted(currentCount), data));
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private NotificationView toView(Notification notification) {
        Object data = notification.getData() == null ? null : objectMapper.readValue(notification.getData(), Object.class);
        return new NotificationView(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getBody(), data, notification.getReadAt(),
                notification.getCreatedAt().atZone(ASIA_SEOUL).toOffsetDateTime());
    }
}
