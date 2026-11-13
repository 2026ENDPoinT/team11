package com.team11.backend.events.dto;

import java.util.List;

public record EventListResponse(List<EventView> events) {
}
