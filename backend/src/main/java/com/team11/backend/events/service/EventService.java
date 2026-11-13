package com.team11.backend.events.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.Kst;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.events.domain.Event;
import com.team11.backend.events.dto.EventListResponse;
import com.team11.backend.events.dto.EventRequest;
import com.team11.backend.events.dto.EventView;
import com.team11.backend.events.repository.EventRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventService {

    private static final long MAX_RANGE_DAYS = 93; // 월 캘린더 + 앞뒤 여유

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /** 월 캘린더는 그 달의 from/to로 요청해 점·밴드를 프론트가 그린다. */
    @Transactional(readOnly = true)
    public EventListResponse list(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.DATE_RANGE_TOO_LARGE);
        }
        return new EventListResponse(between(userId, Kst.startOfDay(from), Kst.startOfDay(to.plusDays(1))).stream()
                .map(EventView::from).toList());
    }

    /** 다른 도메인(플랜)용: [start, end)와 겹치는 일정. */
    @Transactional(readOnly = true)
    public List<Event> between(Long userId, OffsetDateTime start, OffsetDateTime end) {
        return eventRepository.findOverlapping(userId, start, end);
    }

    @Transactional(readOnly = true)
    public EventView get(Long userId, Long eventId) {
        return EventView.from(findOwned(userId, eventId));
    }

    /** 플랜용: 본인 소유 일정 조회(없으면 EVENT_NOT_FOUND, 타인 것이면 AUTH_FORBIDDEN). */
    @Transactional(readOnly = true)
    public Event findOwned(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return event;
    }

    public EventView create(Long userId, EventRequest request) {
        validateRange(request);
        Event event = new Event(userId, request.title().trim(), request.type(), request.startAt(), request.endAt(),
                request.allDayOrDefault());
        return EventView.from(eventRepository.save(event));
    }

    public EventView update(Long userId, Long eventId, EventRequest request) {
        validateRange(request);
        Event event = findOwned(userId, eventId);
        event.update(request.title().trim(), request.type(), request.startAt(), request.endAt(), request.allDayOrDefault());
        return EventView.from(event);
    }

    public void delete(Long userId, Long eventId) {
        eventRepository.delete(findOwned(userId, eventId));
    }

    private static void validateRange(EventRequest request) {
        if (request.endAt() != null && !request.startAt().isBefore(request.endAt())) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
    }
}
