package com.team11.backend.stats.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record UpcomingRisksResponse(List<Risk> risks) {

    public record Risk(LocalDate date, String type, String severity, String title, String message,
                        Long relatedEventId, Suggestion suggestion) {
    }

    public record Suggestion(OffsetDateTime recommendedBedAt) {
    }
}
