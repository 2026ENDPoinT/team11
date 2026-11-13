package com.team11.backend.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** EXAM-02 필드 중 수정할 것만 보냄 — null은 미변경. {@code exams}를 보내면 배열 전체를 교체. */
public record UpdateExamPeriodRequest(
        @Size(min = 1, max = 30, message = "1~30자여야 해요.")
        String name,

        LocalDate from,
        LocalDate to,

        @Valid
        List<CreateExamPeriodRequest.ExamEntry> exams,

        @Valid
        CreateExamPeriodRequest.Constraints constraints
) {
}
