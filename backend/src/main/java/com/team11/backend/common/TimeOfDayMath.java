package com.team11.backend.common;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/** 자정 넘는 취침/기상 시각을 평균·중앙값 등으로 계산할 때 쓰는 변환. 정오(12:00) 이전 시각은 다음날로 보고 +24h 해서 연속축으로 바꾼다. */
public final class TimeOfDayMath {

    private TimeOfDayMath() {
    }

    public static int continuousMinuteOfDay(OffsetDateTime time) {
        LocalTime lt = time.toLocalTime();
        int minuteOfDay = lt.getHour() * 60 + lt.getMinute();
        return minuteOfDay < 12 * 60 ? minuteOfDay + 1440 : minuteOfDay;
    }
}
