package com.team11.backend.events.dto;

import com.team11.backend.events.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 3-3 일정 추가/수정. 종일 일정은 allDay=true + start/end를 그 날 00:00(+09:00)로. 생성/PUT 공용. */
public record EventRequest(
        @NotBlank @Size(max = 100, message = "100자 이내여야 해요.") String title,
        @NotNull EventType type,
        @NotNull OffsetDateTime startAt,
        OffsetDateTime endAt,
        Boolean allDay
) {

    public boolean allDayOrDefault() {
        return Boolean.TRUE.equals(allDay);
    }
}
