package com.team11.backend.events.dto;

import com.team11.backend.events.domain.EventTag;
import com.team11.backend.events.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateEventRequest(
        @NotNull EventType type,

        @NotBlank @Size(min = 1, max = 50, message = "1~50자여야 해요.")
        String title,

        @NotNull OffsetDateTime startAt,
        OffsetDateTime endAt,
        Boolean allDay,
        EventTag tag,

        @Size(max = 200, message = "200자 이내여야 해요.")
        String note,

        Object repeat // 2차 필드. 보내도 그냥 무시(저장 안 함) — 모르는 필드로 거절되지 않게만 받아둠
) {

    public boolean allDayOrDefault() {
        return Boolean.TRUE.equals(allDay);
    }
}
