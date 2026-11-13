package com.team11.backend.exam.dto;

import java.util.List;

public record ExamPeriodListResponse(List<ExamPeriodView.Summary> examPeriods) {
}
