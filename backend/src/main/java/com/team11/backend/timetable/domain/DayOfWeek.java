package com.team11.backend.timetable.domain;

/** 선언 순서가 그대로 정렬 순서(MON~SUN)가 된다. java.time.DayOfWeek은 "MONDAY" 풀네임이라 API 표기(MON)와 안 맞아 직접 정의. */
public enum DayOfWeek {
    MON, TUE, WED, THU, FRI, SAT, SUN
}
