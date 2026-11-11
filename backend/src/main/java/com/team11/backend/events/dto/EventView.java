package com.team11.backend.events.dto;

import com.team11.backend.common.domain.Source;
import com.team11.backend.events.domain.Event;
import com.team11.backend.events.domain.EventTag;
import com.team11.backend.events.domain.EventType;
import java.time.OffsetDateTime;

public record EventView(
        Long eventId, EventType type, String title, OffsetDateTime startAt, OffsetDateTime endAt, boolean allDay,
        EventTag tag, String note, RepeatView repeat, Source source, String externalId
) {

    public static EventView from(Event event) {
        return new EventView(event.getId(), event.getType(), event.getTitle(), event.getStartAt(), event.getEndAt(),
                event.isAllDay(), event.getTag(), event.getNote(), RepeatView.none(), event.getSource(),
                event.getExternalId());
    }
}
