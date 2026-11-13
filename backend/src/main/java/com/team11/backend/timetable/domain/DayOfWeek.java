package com.team11.backend.timetable.domain;

/** 선언 순서가 정렬 순서(MON~SUN). java.time.DayOfWeek은 "MONDAY" 풀네임이라 API/AI 표기(MON)와 안 맞아 직접 정의. */
public enum DayOfWeek {
    MON, TUE, WED, THU, FRI, SAT, SUN;

    public static DayOfWeek of(java.time.DayOfWeek day) {
        return values()[day.getValue() - 1];
    }
}
