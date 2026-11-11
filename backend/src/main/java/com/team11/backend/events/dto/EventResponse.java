package com.team11.backend.events.dto;

import java.util.List;

public record EventResponse(EventView event, List<AffectedPlanView> affectedPlans) {

    public static EventResponse of(EventView event) {
        return new EventResponse(event, List.of());
    }
}
