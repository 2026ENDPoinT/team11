package com.team11.backend.events.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.events.domain.EventTag;
import com.team11.backend.events.domain.EventType;
import com.team11.backend.events.dto.CreateEventRequest;
import com.team11.backend.events.dto.EventListResponse;
import com.team11.backend.events.dto.EventResponse;
import com.team11.backend.events.dto.EventView;
import com.team11.backend.events.dto.UpdateEventRequest;
import com.team11.backend.events.service.EventService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EventListResponse>> getEvents(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) EventType type,
            @RequestParam(required = false) EventTag tag) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getEvents(userId, from, to, type, tag)));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventView>> getEvent(@AuthenticationPrincipal Long userId,
                                                             @PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getEvent(userId, eventId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(@AuthenticationPrincipal Long userId,
                                                                     @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(eventService.createEvent(userId, request)));
    }

    @PatchMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(@AuthenticationPrincipal Long userId,
                                                                     @PathVariable Long eventId,
                                                                     @Valid @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success(eventService.updateEvent(userId, eventId, request)));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@AuthenticationPrincipal Long userId, @PathVariable Long eventId) {
        eventService.deleteEvent(userId, eventId);
        return ResponseEntity.noContent().build();
    }
}
