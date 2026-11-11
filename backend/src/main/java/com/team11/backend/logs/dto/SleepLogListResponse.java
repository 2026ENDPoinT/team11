package com.team11.backend.logs.dto;

import java.util.List;

public record SleepLogListResponse(List<SleepLogView> logs, Summary summary) {

    public record Summary(int count, Integer avgDurationMin, String avgBedTime, String avgWakeTime) {
    }
}
