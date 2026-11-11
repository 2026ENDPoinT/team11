package com.team11.backend.events.dto;

import java.time.LocalDate;

/** Plans 도메인이 생기기 전까지는 항상 빈 리스트로만 쓰인다. */
public record AffectedPlanView(Long planId, LocalDate targetDate) {
}
