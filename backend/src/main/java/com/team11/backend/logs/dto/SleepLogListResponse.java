package com.team11.backend.logs.dto;

import java.util.List;

public record SleepLogListResponse(List<SleepLogView> logs) {
}
