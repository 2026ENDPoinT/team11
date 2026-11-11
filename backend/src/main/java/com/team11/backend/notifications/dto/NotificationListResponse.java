package com.team11.backend.notifications.dto;

import java.util.List;

public record NotificationListResponse(List<NotificationView> items, String nextCursor, boolean hasNext) {
}
