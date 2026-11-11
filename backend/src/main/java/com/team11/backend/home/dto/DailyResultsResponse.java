package com.team11.backend.home.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DailyResultsResponse(List<DailyResultView> results) {

    public record DailyResultView(LocalDate date, boolean goalMet, OffsetDateTime recommendedBedAt,
                                   OffsetDateTime actualBedAt, Integer deviationMin, Integer durationMin,
                                   boolean finalized) {
    }
}
