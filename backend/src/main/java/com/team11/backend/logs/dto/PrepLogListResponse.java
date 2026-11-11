package com.team11.backend.logs.dto;

import java.util.List;

public record PrepLogListResponse(List<PrepLogView> logs, Integer avgPrepMin, Integer prepMinEstimate) {
}
