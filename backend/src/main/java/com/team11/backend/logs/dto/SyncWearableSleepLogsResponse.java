package com.team11.backend.logs.dto;

import java.util.List;

public record SyncWearableSleepLogsResponse(int syncedCount, int skippedCount, List<SleepLogView> logs) {
}
