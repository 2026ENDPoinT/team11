package com.team11.backend.reports.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** WEEK = date가 속한 월~일, MONTH = date가 속한 달. */
public enum ReportPeriod {
    WEEK, MONTH;

    public LocalDate from(LocalDate date) {
        return this == WEEK ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : date.withDayOfMonth(1);
    }

    public LocalDate to(LocalDate date) {
        return this == WEEK ? from(date).plusDays(6) : date.with(TemporalAdjusters.lastDayOfMonth());
    }
}
