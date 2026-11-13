package com.team11.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * AI-02/03이 공통으로 받는 원시 스냅샷 행. 두 계약이 쓰는 필드의 합집합을 담고(모르는 필드는 AI가 무시) null은 생략한다.
 * 이름·아이디 등 개인 식별 정보는 들어가지 않는다.
 */
public final class AiSnapshot {

    private AiSnapshot() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Event(Long eventId, String type, OffsetDateTime startAt, OffsetDateTime endAt, boolean allDay) {
    }

    /** startTime/endTime은 "HH:mm" 문자열(AI-02가 HH:mm만 읽음). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimetableEntry(Long entryId, String dayOfWeek, String startTime, String endTime, String semester,
                                  String courseName) {
    }

    /** quality는 AI 척도(1~5). 앱의 컨디션 3단계는 호출부가 1/3/5로 바꿔서 넣는다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SleepLog(Long sleepLogId, LocalDate date, OffsetDateTime bedAt, OffsetDateTime wakeAt,
                            int durationMin, Integer quality, String status, String source) {
    }

    public record Semester(String semester, LocalDate startDate, LocalDate endDate, boolean current) {
    }
}
