package com.team11.backend.common;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 자정 넘는 취침/기상 시각을 평균·중앙값으로 계산할 때 쓰는 변환. 정오 이전 시각은 다음날로 보고 +24h 해서 연속축으로 바꾼다. */
public final class TimeOfDayMath {

    private TimeOfDayMath() {
    }

    public static int continuousMinuteOfDay(OffsetDateTime time) {
        LocalTime lt = Kst.timeOf(time);
        int minuteOfDay = lt.getHour() * 60 + lt.getMinute();
        return minuteOfDay < 12 * 60 ? minuteOfDay + 1440 : minuteOfDay;
    }

    /** 연속축 분 평균을 다시 "HH:mm"으로. 비어 있으면 null. */
    public static String averageClock(List<OffsetDateTime> times) {
        if (times.isEmpty()) {
            return null;
        }
        double avg = times.stream().mapToInt(TimeOfDayMath::continuousMinuteOfDay).average().orElse(0);
        return clock((int) Math.round(avg));
    }

    public static String clock(int continuousMinute) {
        int m = ((continuousMinute % 1440) + 1440) % 1440;
        return "%02d:%02d".formatted(m / 60, m % 60);
    }
}
