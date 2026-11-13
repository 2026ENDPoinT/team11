package com.team11.backend.events.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.events.dto.EventListResponse;
import com.team11.backend.events.dto.EventRequest;
import com.team11.backend.events.dto.EventView;
import com.team11.backend.events.service.EventService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    public ApiResponse<EventListResponse> list(@AuthenticationPrincipal Long userId,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(eventService.list(userId, from, to));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventView>> create(@AuthenticationPrincipal Long userId,
                                                          @Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(eventService.create(userId, request)));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventView> get(@AuthenticationPrincipal Long userId, @PathVariable Long eventId) {
        return ApiResponse.success(eventService.get(userId, eventId));
    }

    @PutMapping("/{eventId}")
    public ApiResponse<EventView> update(@AuthenticationPrincipal Long userId, @PathVariable Long eventId,
                                         @Valid @RequestBody EventRequest request) {
        return ApiResponse.success(eventService.update(userId, eventId, request));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long eventId) {
        eventService.delete(userId, eventId);
        return ResponseEntity.noContent().build();
    }
}
