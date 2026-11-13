package com.team11.backend.reports.dto;

import java.time.LocalDate;
import java.util.List;

/** 4 리포트 화면: 막대그래프(daily) + 지표 3개(평균 수면·평균 취침·규칙성) + 하단 목록. 기록 없는 날은 sleepMin=null. */
public record ReportResponse(ReportPeriod period, LocalDate from, LocalDate to, int loggedDays, Integer avgSleepMin,
                             String avgBedTime, Integer regularityPct, List<Daily> daily) {

    public record Daily(LocalDate date, Long sleepLogId, Integer sleepMin, Integer quality) {
    }
}
