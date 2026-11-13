package com.team11.backend.reports.service;

import com.team11.backend.common.TimeOfDayMath;
import com.team11.backend.logs.domain.SleepLog;
import com.team11.backend.logs.service.SleepLogService;
import com.team11.backend.reports.dto.ReportPeriod;
import com.team11.backend.reports.dto.ReportResponse;
import com.team11.backend.reports.dto.ReportResponse.Daily;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 새 테이블 없이 sleep_logs만 집계한다. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final SleepLogService sleepLogService;

    public ReportService(SleepLogService sleepLogService) {
        this.sleepLogService = sleepLogService;
    }

    public ReportResponse report(Long userId, ReportPeriod period, LocalDate date) {
        LocalDate from = period.from(date);
        LocalDate to = period.to(date);
        List<SleepLog> logs = sleepLogService.between(userId, from, to);
        Map<LocalDate, SleepLog> byDate = logs.stream().collect(Collectors.toMap(SleepLog::getDate, Function.identity()));

        List<Daily> daily = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            SleepLog log = byDate.get(d);
            daily.add(log == null ? new Daily(d, null, null, null)
                    : new Daily(d, log.getId(), log.getDurationMin(), log.getQuality()));
        }

        Integer avgSleepMin = logs.isEmpty() ? null
                : (int) Math.round(logs.stream().mapToInt(SleepLog::getDurationMin).average().orElse(0));
        String avgBedTime = TimeOfDayMath.averageClock(logs.stream().map(SleepLog::getBedAt).toList());
        Integer regularity = regularityPct(logs.stream().map(l -> TimeOfDayMath.continuousMinuteOfDay(l.getBedAt())).toList());

        return new ReportResponse(period, from, to, logs.size(), avgSleepMin, avgBedTime, regularity, daily);
    }

    /**
     * 규칙성(%): 취침시각 중앙값 대비 평균 절대편차(MAD)를 60분으로 정규화 → 100 - min(100, MAD/60*100).
     * 편차 0분 = 100%, 평균 30분 흔들림 = 50%, 60분 이상 = 0%. 기록 2건 미만이면 null.
     * ponytail: 공식 정의가 없어 임시로 정한 규칙 — 팀 리뷰 대상. 바꿀 땐 이 메서드와 ReportServiceTest만.
     */
    public static Integer regularityPct(List<Integer> continuousBedMinutes) {
        if (continuousBedMinutes.size() < 2) {
            return null;
        }
        List<Integer> sorted = continuousBedMinutes.stream().sorted().toList();
        int n = sorted.size();
        double median = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        double mad = sorted.stream().mapToDouble(m -> Math.abs(m - median)).average().orElse(0);
        return (int) Math.round(100 - Math.min(100, mad / 60 * 100));
    }
}
