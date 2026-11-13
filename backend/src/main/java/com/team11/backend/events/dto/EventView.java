package com.team11.backend.events.dto;

import com.team11.backend.events.domain.Event;
import com.team11.backend.events.domain.EventType;
import java.time.OffsetDateTime;

public record EventView(Long eventId, String title, EventType type, OffsetDateTime startAt, OffsetDateTime endAt,
                        boolean allDay) {

    public static EventView from(Event e) {
        return new EventView(e.getId(), e.getTitle(), e.getType(), e.getStartAt(), e.getEndAt(), e.isAllDay());
    }
}
