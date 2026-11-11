package com.team11.backend.events.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.domain.Source;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.events.domain.Event;
import com.team11.backend.events.domain.EventTag;
import com.team11.backend.events.domain.EventType;
import com.team11.backend.events.dto.CreateEventRequest;
import com.team11.backend.events.dto.EventListResponse;
import com.team11.backend.events.dto.EventResponse;
import com.team11.backend.events.dto.EventView;
import com.team11.backend.events.dto.UpdateEventRequest;
import com.team11.backend.events.repository.EventRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final long MAX_RANGE_DAYS = 93;

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public EventListResponse getEvents(Long userId, LocalDate from, LocalDate to, EventType type, EventTag tag) {
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.DATE_RANGE_TOO_LARGE);
        }
        OffsetDateTime rangeStart = from.atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime rangeEnd = to.plusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();

        var events = eventRepository.findOverlapping(userId, rangeStart, rangeEnd).stream()
                .filter(e -> type == null || e.getType() == type)
                .filter(e -> tag == null || e.getTag() == tag)
                .map(EventView::from)
                .toList();
        return new EventListResponse(events);
    }

    @Transactional(readOnly = true)
    public EventView getEvent(Long userId, Long eventId) {
        return EventView.from(findOwnedEvent(userId, eventId));
    }

    public EventResponse createEvent(Long userId, CreateEventRequest request) {
        if (request.endAt() != null && !request.startAt().isBefore(request.endAt())) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
        Event event = new Event(userId, request.type(), request.title(), request.startAt(), request.endAt(),
                request.allDayOrDefault(), request.tag(), request.note(), Source.MANUAL, null);
        eventRepository.save(event);
        return EventResponse.of(EventView.from(event));
    }

    public EventResponse updateEvent(Long userId, Long eventId, UpdateEventRequest request) {
        Event event = findOwnedEvent(userId, eventId);

        if (event.getSource() != Source.MANUAL && request.changesReadonlyFields()) {
            throw new ApiException(ErrorCode.EVENT_READONLY_FIELD);
        }

        EventType type = request.type() != null ? request.type() : event.getType();
        String title = request.title() != null ? request.title() : event.getTitle();
        OffsetDateTime startAt = request.startAt() != null ? request.startAt() : event.getStartAt();
        OffsetDateTime endAt = request.endAt() != null ? request.endAt() : event.getEndAt();
        boolean allDay = request.allDay() != null ? request.allDay() : event.isAllDay();
        EventTag tag = request.tag() != null ? request.tag() : event.getTag();
        String note = request.note() != null ? request.note() : event.getNote();

        if (endAt != null && !startAt.isBefore(endAt)) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }

        if (event.getSource() != Source.MANUAL) {
            event.updateTagAndNote(tag, note);
        } else {
            event.updateAll(type, title, startAt, endAt, allDay, tag, note);
        }
        return EventResponse.of(EventView.from(event));
    }

    public void deleteEvent(Long userId, Long eventId) {
        eventRepository.delete(findOwnedEvent(userId, eventId));
    }

    private Event findOwnedEvent(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return event;
    }
}
