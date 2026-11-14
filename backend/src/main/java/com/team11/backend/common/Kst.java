package com.team11.backend.common;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 서울 시간 상수와 날짜↔시각 변환. 날짜·요일 판단은 전부 KST 기준(AI 서버 계약도 동일). */
public final class Kst {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final ZoneOffset OFFSET = ZoneOffset.of("+09:00");

    private Kst() {
    }

    /** 초 단위로 자른다 — AI 서버가 소수점 초가 붙은 시각을 RFC3339로 인정하지 않고 400으로 거절한다(실측). */
    public static OffsetDateTime now() {
        return OffsetDateTime.now(ZONE).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toOffsetDateTime();
    }

    public static OffsetDateTime at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZONE).toOffsetDateTime();
    }

    public static LocalDate dateOf(OffsetDateTime time) {
        return time.atZoneSameInstant(ZONE).toLocalDate();
    }

    public static LocalTime timeOf(OffsetDateTime time) {
        return time.atZoneSameInstant(ZONE).toLocalTime();
    }
}
