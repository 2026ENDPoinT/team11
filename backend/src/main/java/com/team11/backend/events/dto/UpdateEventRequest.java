package com.team11.backend.events.dto;

import com.team11.backend.events.domain.EventTag;
import com.team11.backend.events.domain.EventType;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record UpdateEventRequest(
        EventType type,

        @Size(min = 1, max = 50, message = "1~50자여야 해요.")
        String title,

        OffsetDateTime startAt,
        OffsetDateTime endAt,
        Boolean allDay,
        EventTag tag,

        @Size(max = 200, message = "200자 이내여야 해요.")
        String note
) {

    /** source=ECLASS는 tag/note만 수정 가능("~만 수정 가능"이라 title/allDay도 막음). */
    public boolean changesReadonlyFields() {
        return type != null || title != null || startAt != null || endAt != null || allDay != null;
    }
}
