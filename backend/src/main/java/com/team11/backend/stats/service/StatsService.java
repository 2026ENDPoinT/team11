package com.team11.backend.stats.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.TimeOfDayMath;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.events.domain.Event;
import com.team11.backend.events.domain.EventTag;
import com.team11.backend.events.domain.EventType;
import com.team11.backend.events.repository.EventRepository;
import com.team11.backend.home.dto.DailyResultsResponse.DailyResultView;
import com.team11.backend.home.service.StreakService;
import com.team11.backend.home.service.StreakService.StreakCounts;
import com.team11.backend.logs.domain.PrepLog;
import com.team11.backend.logs.domain.SleepLog;
import com.team11.backend.logs.repository.PrepLogRepository;
import com.team11.backend.logs.repository.SleepLogRepository;
import com.team11.backend.plans.domain.Plan;
import com.team11.backend.plans.domain.PlanStatus;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.plans.repository.PlanRepository;
import com.team11.backend.plans.service.PlanService;
import com.team11.backend.stats.dto.ChronotypeEstimateResponse;
import com.team11.backend.stats.dto.SleepDebtResponse;
import com.team11.backend.stats.dto.SleepDebtResponse.DailyDebt;
import com.team11.backend.stats.dto.SleepDebtResponse.Recovery;
import com.team11.backend.stats.dto.TrendResponse;
import com.team11.backend.stats.dto.TrendResponse.Point;
import com.team11.backend.stats.dto.UpcomingRisksResponse;
import com.team11.backend.stats.dto.UpcomingRisksResponse.Risk;
import com.team11.backend.stats.dto.UpcomingRisksResponse.Suggestion;
import com.team11.backend.stats.dto.WeeklyStatsResponse;
import com.team11.backend.stats.dto.WeeklyStatsResponse.StreakSummary;
import com.team11.backend.stats.dto.WeeklyStatsResponse.VsLastWeek;
import com.team11.backend.timetable.config.SemesterProperties;
import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.timetable.repository.TimetableEntryRepository;
import com.team11.backend.users.domain.Chronotype;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.repository.UserRepository;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI 없이 Spring 규칙으로만 계산(STAT-04 각성도 예측만 AI 필수라 제외). CLAUDE.md §10 참고. */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalTime SETTLEMENT_CUTOFF = LocalTime.of(6, 0);
    private static final LocalTime EARLY_COMMITMENT_CUTOFF = LocalTime.of(9, 0);
    private static final LocalTime LATE_EVENT_THRESHOLD = LocalTime.of(23, 0);
    private static final LocalTime DRINKING_HIGH_THRESHOLD = LocalTime.of(23, 30);
    private static final LocalTime DRINKING_MEDIUM_THRESHOLD = LocalTime.of(22, 0);
    private static final int DEBT_MEDIUM_THRESHOLD_MIN = 180;
    private static final int DEBT_HIGH_THRESHOLD_MIN = 300;
    private static final int CHRONOTYPE_LOOKBACK_DAYS = 35;
    private static final int CHRONOTYPE_MIN_DAYS = 7;
    private static final int CHRONOTYPE_MEDIUM_DAYS = 14;
    private static final int CHRONOTYPE_HIGH_DAYS = 28;

    private final UserRepository userRepository;
    private final SleepLogRepository sleepLogRepository;
    private final PrepLogRepository prepLogRepository;
    private final EventRepository eventRepository;
    private final PlanRepository planRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final SemesterProperties semesterProperties;
    private final StreakService streakService;
    private final PlanService planService;

    public StatsService(UserRepository userRepository, SleepLogRepository sleepLogRepository,
                         PrepLogRepository prepLogRepository, EventRepository eventRepository,
                         PlanRepository planRepository, TimetableEntryRepository timetableEntryRepository,
                         SemesterProperties semesterProperties, StreakService streakService,
                         PlanService planService) {
        this.userRepository = userRepository;
        this.sleepLogRepository = sleepLogRepository;
        this.prepLogRepository = prepLogRepository;
        this.eventRepository = eventRepository;
        this.planRepository = planRepository;
        this.timetableEntryRepository = timetableEntryRepository;
        this.semesterProperties = semesterProperties;
        this.streakService = streakService;
        this.planService = planService;
    }

    // ---------- STAT-01 ----------

    public SleepDebtResponse getSleepDebt(Long userId, String rangeParam) {
        int days = parseRangeDays(rangeParam);
        User user = findUser(userId);
        LocalDate today = LocalDate.now(ASIA_SEOUL);
        LocalDate from = today.minusDays(days - 1);

        Map<LocalDate, Integer> sleptByDate = new LinkedHashMap<>();
        for (SleepLog log : sleepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, from, today)) {
            sleptByDate.putIfAbsent(log.getDate(), log.getDurationMin());
        }

        List<DailyDebt> daily = new ArrayList<>();
        int missingDays = 0;
        int cumulative = 0;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            Integer slept = sleptByDate.get(d);
            if (slept == null) {
                missingDays++;
                continue;
            }
            int delta = slept - user.getSleepNeedMin();
            cumulative = Math.max(0, cumulative - delta);
            daily.add(new DailyDebt(d, slept, user.getSleepNeedMin(), delta, cumulative));
        }

        if (daily.size() < 2) {
            return new SleepDebtResponse(rangeParam, user.getSleepNeedMin(), 0, missingDays, List.of(), null, true);
        }

        int currentDebtMin = daily.get(daily.size() - 1).cumulativeDebtMin();
        Recovery recovery = currentDebtMin > 0
                ? new Recovery("이번 주말 하루 +60분, 다음 주 평일 +15분씩 보충해 보세요.", 20)
                : null;
        return new SleepDebtResponse(rangeParam, user.getSleepNeedMin(), currentDebtMin, missingDays, daily, recovery,
                false);
    }

    private int parseRangeDays(String range) {
        if ("7d".equals(range)) {
            return 7;
        }
        if ("14d".equals(range)) {
            return 14;
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED);
    }

    // ---------- STAT-02 ----------

    private record WeekMetrics(int loggedDays, Integer avgSleepMin, String avgBedTime, String avgWakeTime,
                                Integer bedTimeStdMin, int goalMetDays, int goalDays, Integer avgPrepMin) {
    }

    public WeeklyStatsResponse getWeeklyStats(Long userId, String weekStartParam) {
        LocalDate weekStart = parseWeekStart(weekStartParam);
        WeekMetrics current = computeWeekMetrics(userId, weekStart);
        boolean insufficientData = current.loggedDays() < 3;

        VsLastWeek vsLastWeek = null;
        if (!insufficientData) {
            WeekMetrics last = computeWeekMetrics(userId, weekStart.minusDays(7));
            vsLastWeek = new VsLastWeek(diff(current.avgSleepMin(), last.avgSleepMin()),
                    diff(current.bedTimeStdMin(), last.bedTimeStdMin()), current.goalMetDays() - last.goalMetDays());
        }

        StreakCounts streakCounts = streakService.getCurrentAndBest(userId);
        List<String> highlights = buildHighlights(current);

        return new WeeklyStatsResponse(weekStart, current.loggedDays(), current.avgSleepMin(), current.avgBedTime(),
                current.avgWakeTime(), current.bedTimeStdMin(), current.goalMetDays(), current.goalDays(),
                new StreakSummary(streakCounts.currentCount(), streakCounts.bestCount()), current.avgPrepMin(),
                vsLastWeek, highlights, insufficientData);
    }

    private WeekMetrics computeWeekMetrics(Long userId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);

        Map<LocalDate, SleepLog> sleepByDate = new LinkedHashMap<>();
        for (SleepLog log : sleepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, weekStart, weekEnd)) {
            sleepByDate.putIfAbsent(log.getDate(), log);
        }
        List<SleepLog> sleepLogs = new ArrayList<>(sleepByDate.values());

        Integer avgSleepMin = sleepLogs.isEmpty() ? null
                : (int) sleepLogs.stream().mapToInt(SleepLog::getDurationMin).average().orElse(0);
        String avgBedTime = averageTimeOfDay(sleepLogs.stream().map(SleepLog::getBedAt).toList());
        String avgWakeTime = averageTimeOfDay(sleepLogs.stream().map(SleepLog::getWakeAt).toList());
        Integer bedTimeStdMin = stdDevMinutes(sleepLogs.stream().map(SleepLog::getBedAt).toList());

        Set<LocalDate> planDates = planRepository
                .findByUserIdAndTargetDateBetweenAndStatus(userId, weekStart, weekEnd, PlanStatus.ACTIVE).stream()
                .map(Plan::getTargetDate).collect(Collectors.toSet());
        Map<LocalDate, Boolean> goalMetByDate = streakService.getDailyResults(userId, weekStart, weekEnd).results()
                .stream().collect(Collectors.toMap(DailyResultView::date, DailyResultView::goalMet));
        int goalMetDays = (int) planDates.stream().filter(d -> Boolean.TRUE.equals(goalMetByDate.get(d))).count();

        List<PrepLog> prepLogs = prepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, weekStart, weekEnd);
        Integer avgPrepMin = prepLogs.isEmpty() ? null
                : (int) prepLogs.stream().mapToInt(PrepLog::getPrepMin).average().orElse(0);

        return new WeekMetrics(sleepLogs.size(), avgSleepMin, avgBedTime, avgWakeTime, bedTimeStdMin, goalMetDays,
                planDates.size(), avgPrepMin);
    }

    private LocalDate parseWeekStart(String weekStartParam) {
        LocalDate today = LocalDate.now(ASIA_SEOUL);
        if (weekStartParam == null || weekStartParam.isBlank()) {
            return today.with(java.time.DayOfWeek.MONDAY).minusDays(7);
        }
        LocalDate weekStart;
        try {
            weekStart = LocalDate.parse(weekStartParam);
        } catch (DateTimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (weekStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY || weekStart.isAfter(today)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return weekStart;
    }

    private List<String> buildHighlights(WeekMetrics m) {
        if (m.goalDays() == 0) {
            return List.of();
        }
        int pct = Math.round(m.goalMetDays() * 100f / m.goalDays());
        return List.of("목표 달성률 %d%%".formatted(pct));
    }

    // ---------- STAT-03 ----------

    private static final Set<String> VALID_RANGES = Set.of("7d", "30d", "90d");
    private static final Set<String> VALID_METRICS = Set.of("sleepMin", "bedTime", "wakeTime", "quality", "prepMin");

    public TrendResponse getTrend(Long userId, String rangeParam, String metricParam) {
        if (!VALID_RANGES.contains(rangeParam) || !VALID_METRICS.contains(metricParam)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        int days = Integer.parseInt(rangeParam.substring(0, rangeParam.length() - 1));
        LocalDate today = LocalDate.now(ASIA_SEOUL);
        LocalDate from = today.minusDays(days - 1);

        Map<LocalDate, Integer> valueByDate = new TreeMap<>();
        if ("prepMin".equals(metricParam)) {
            for (PrepLog log : prepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, from, today)) {
                valueByDate.put(log.getDate(), log.getPrepMin());
            }
        } else {
            for (SleepLog log : sleepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, from, today)) {
                Integer value = switch (metricParam) {
                    case "sleepMin" -> log.getDurationMin();
                    case "bedTime" -> log.getBedAt() == null ? null : TimeOfDayMath.continuousMinuteOfDay(log.getBedAt());
                    case "wakeTime" -> log.getWakeAt() == null ? null : TimeOfDayMath.continuousMinuteOfDay(log.getWakeAt());
                    case "quality" -> log.getQuality();
                    default -> null;
                };
                if (value != null) {
                    valueByDate.putIfAbsent(log.getDate(), value);
                }
            }
        }

        List<Point> points = valueByDate.entrySet().stream().map(e -> new Point(e.getKey(), e.getValue())).toList();

        List<Point> movingAvg = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            List<Integer> windowValues = new ArrayList<>();
            for (LocalDate wd = d.minusDays(6); !wd.isAfter(d); wd = wd.plusDays(1)) {
                Integer v = valueByDate.get(wd);
                if (v != null) {
                    windowValues.add(v);
                }
            }
            if (windowValues.size() >= 4) {
                int avg = Math.round((float) windowValues.stream().mapToInt(Integer::intValue).average().orElse(0));
                movingAvg.add(new Point(d, avg));
            }
        }

        return new TrendResponse(metricParam, rangeParam, points, movingAvg);
    }

    // ---------- STAT-05 ----------

    /** suggestionFor()가 오늘 밤 Plan을 새로 만들 수 있어 클래스 레벨 readOnly를 여기서만 덮어쓴다. */
    @Transactional
    public UpcomingRisksResponse getUpcomingRisks(Long userId, Integer daysParam) {
        int days = daysParam != null ? daysParam : 3;
        if (days < 1 || days > 7) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }

        LocalDate today = LocalDate.now(ASIA_SEOUL);
        LocalDate lastDate = today.plusDays(days - 1);
        OffsetDateTime queryStart = today.atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime queryEnd = lastDate.plusDays(2).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        List<Event> events = eventRepository.findOverlapping(userId, queryStart, queryEnd);
        Map<DayOfWeek, List<TimetableEntry>> entriesByDayOfWeek = entriesByDayOfWeek(userId, today);

        List<Risk> risks = new ArrayList<>();
        for (LocalDate date = today; !date.isAfter(lastDate); date = date.plusDays(1)) {
            LocalDate nextDay = date.plusDays(1);
            boolean earlyCommitmentNextDay = hasEarlyCommitment(events, entriesByDayOfWeek, nextDay);

            Event drinking = findOnDate(events, date, EventTag.DRINKING);
            if (drinking != null) {
                risks.add(earlyCommitmentNextDay ? earlyClassRisk(userId, date, drinking)
                        : drinkingRisk(userId, date, drinking));
                continue;
            }

            Event lateEvent = findLateEvent(events, date);
            if (lateEvent != null) {
                risks.add(earlyCommitmentNextDay ? earlyClassRisk(userId, date, lateEvent)
                        : lateEventRisk(userId, date, lateEvent));
                continue;
            }

            Event examNextDay = findExamOn(events, nextDay);
            if (examNextDay != null) {
                risks.add(examRisk(userId, date, examNextDay));
            }
        }

        SleepDebtResponse debt = getSleepDebt(userId, "7d");
        if (!debt.insufficientData() && debt.currentDebtMin() > DEBT_MEDIUM_THRESHOLD_MIN) {
            risks.add(debtRisk(userId, today, debt.currentDebtMin()));
        }

        risks.sort(Comparator.comparing(Risk::date).thenComparing((Risk r) -> severityRank(r.severity())).reversed()
                .thenComparing(Risk::date));
        return new UpcomingRisksResponse(risks);
    }

    private Risk drinkingRisk(Long userId, LocalDate date, Event event) {
        OffsetDateTime end = event.getEndAt() != null ? event.getEndAt() : event.getStartAt();
        String severity = !end.toLocalTime().isBefore(DRINKING_HIGH_THRESHOLD) ? "HIGH"
                : !end.toLocalTime().isBefore(DRINKING_MEDIUM_THRESHOLD) ? "MEDIUM" : "LOW";
        String message = "%s에 끝나면 다음 날 일정까지 수면을 확보하기 어려울 수 있어요. 오후에 낮잠이나 일찍 귀가를 고려해 보세요."
                .formatted(end.toLocalTime());
        return new Risk(date, "DRINKING", severity, "술 약속이 있어요", message, event.getId(),
                suggestionFor(userId, date));
    }

    private Risk lateEventRisk(Long userId, LocalDate date, Event event) {
        OffsetDateTime end = event.getEndAt() != null ? event.getEndAt() : event.getStartAt();
        String message = "%s에 끝나는 일정이 있어요. 평소보다 늦게 잠들 수 있어요.".formatted(end.toLocalTime());
        return new Risk(date, "LATE_EVENT", "MEDIUM", "늦은 일정이 있어요", message, event.getId(),
                suggestionFor(userId, date));
    }

    private Risk earlyClassRisk(Long userId, LocalDate date, Event lateEvent) {
        String message = "오늘 늦은 일정과 다음 날 이른 일정이 겹쳐요. 수면 시간이 많이 부족할 수 있어요.";
        return new Risk(date, "EARLY_CLASS_AFTER_LATE", "HIGH", "이른 일정 전날 늦은 일정이 있어요", message,
                lateEvent.getId(), suggestionFor(userId, date));
    }

    private Risk examRisk(Long userId, LocalDate date, Event exam) {
        String message = "내일 %s 시험이 있어요. 오늘은 일찍 자는 게 좋겠어요.".formatted(exam.getTitle());
        return new Risk(date, "EXAM_NEXT_DAY", "MEDIUM", "내일 시험이 있어요", message, exam.getId(),
                suggestionFor(userId, date));
    }

    private Risk debtRisk(Long userId, LocalDate date, int currentDebtMin) {
        String severity = currentDebtMin > DEBT_HIGH_THRESHOLD_MIN ? "HIGH" : "MEDIUM";
        String message = "최근 7일간 수면 부채가 %d분이에요. 오늘은 평소보다 일찍 자는 게 좋겠어요.".formatted(currentDebtMin);
        return new Risk(date, "DEBT_HIGH", severity, "수면 부채가 쌓였어요", message, null, suggestionFor(userId, date));
    }

    private Suggestion suggestionFor(Long userId, LocalDate date) {
        LocalDate tonightTargetDate = tonightTargetDate();
        if (!date.equals(tonightTargetDate)) {
            return null;
        }
        PlanView plan = planService.getTonightPlan(userId);
        OffsetDateTime target = plan.userBedAt() != null ? plan.userBedAt() : plan.recommendedBedAt();
        return new Suggestion(target);
    }

    private LocalDate tonightTargetDate() {
        OffsetDateTime now = OffsetDateTime.now(ASIA_SEOUL);
        return now.toLocalTime().isBefore(SETTLEMENT_CUTOFF) ? now.toLocalDate() : now.toLocalDate().plusDays(1);
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "HIGH" -> 2;
            case "MEDIUM" -> 1;
            default -> 0;
        };
    }

    private Event findOnDate(List<Event> events, LocalDate date, EventTag tag) {
        return events.stream()
                .filter(e -> e.getTag() == tag)
                .filter(e -> e.getStartAt().toLocalDate().equals(date))
                .max(Comparator.comparing(e -> e.getEndAt() != null ? e.getEndAt() : e.getStartAt()))
                .orElse(null);
    }

    private Event findLateEvent(List<Event> events, LocalDate date) {
        return events.stream()
                .filter(e -> !e.isAllDay() && e.getTag() != EventTag.DRINKING)
                .filter(e -> e.getStartAt().toLocalDate().equals(date))
                .filter(e -> {
                    OffsetDateTime end = e.getEndAt() != null ? e.getEndAt() : e.getStartAt();
                    return !end.toLocalTime().isBefore(LATE_EVENT_THRESHOLD);
                })
                .max(Comparator.comparing(e -> e.getEndAt() != null ? e.getEndAt() : e.getStartAt()))
                .orElse(null);
    }

    private Event findExamOn(List<Event> events, LocalDate date) {
        return events.stream()
                .filter(e -> e.getType() == EventType.EXAM)
                .filter(e -> e.getStartAt().toLocalDate().equals(date))
                .findFirst().orElse(null);
    }

    private boolean hasEarlyCommitment(List<Event> events, Map<DayOfWeek, List<TimetableEntry>> entriesByDayOfWeek,
                                        LocalDate date) {
        DayOfWeek dayOfWeek = DayOfWeek.values()[date.getDayOfWeek().getValue() - 1];
        boolean earlyClass = entriesByDayOfWeek.getOrDefault(dayOfWeek, List.of()).stream()
                .anyMatch(entry -> entry.getStartTime().isBefore(EARLY_COMMITMENT_CUTOFF));
        boolean earlyEvent = events.stream()
                .filter(e -> !e.isAllDay() && e.getStartAt().toLocalDate().equals(date))
                .anyMatch(e -> e.getStartAt().toLocalTime().isBefore(EARLY_COMMITMENT_CUTOFF));
        return earlyClass || earlyEvent;
    }

    private Map<DayOfWeek, List<TimetableEntry>> entriesByDayOfWeek(Long userId, LocalDate anyDate) {
        String semesterCode = semesterProperties.resolveCodeFor(anyDate);
        if (semesterCode == null) {
            return Map.of();
        }
        return timetableEntryRepository.findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(userId, semesterCode)
                .stream().collect(Collectors.groupingBy(TimetableEntry::getDayOfWeek));
    }

    // ---------- STAT-06 ----------

    public ChronotypeEstimateResponse getChronotypeEstimate(Long userId) {
        User user = findUser(userId);
        LocalDate from = LocalDate.now(ASIA_SEOUL).minusDays(CHRONOTYPE_LOOKBACK_DAYS);
        LocalDate to = LocalDate.now(ASIA_SEOUL);

        List<SleepLog> logs = sleepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, from, to);
        int basedOnDays = logs.size();

        if (basedOnDays < CHRONOTYPE_MIN_DAYS) {
            return new ChronotypeEstimateResponse(user.getChronotype(), null, "LOW", basedOnDays, null, true, true);
        }

        double avgMidSleepMinutes = logs.stream()
                .mapToInt(log -> midSleepContinuousMinutes(log.getBedAt(), log.getDurationMin()))
                .average().orElse(0);
        int midSleepMin = (int) Math.round(avgMidSleepMinutes) % 1440;
        String midSleepFreeday = "%02d:%02d".formatted(midSleepMin / 60, midSleepMin % 60);

        Chronotype estimated = classifyChronotype(midSleepMin);
        String confidence = basedOnDays >= CHRONOTYPE_HIGH_DAYS ? "HIGH"
                : basedOnDays >= CHRONOTYPE_MEDIUM_DAYS ? "MEDIUM" : "LOW";
        boolean applied = estimated == user.getChronotype();
        String suggestion = applied ? null : "단기간에 리듬을 크게 바꾸기보다 취침을 매일 15분씩 앞당겨 보세요.";

        return new ChronotypeEstimateResponse(estimated, midSleepFreeday, confidence, basedOnDays, suggestion,
                applied, false);
    }

    /** 자정 기준(0~1440분, 정오로 안 밀지 않음) — 수면 중앙시각은 보통 새벽 시간대라 바로 비교하기 쉽다. */
    private int midSleepContinuousMinutes(OffsetDateTime bedAt, Integer durationMin) {
        int bedMinute = TimeOfDayMath.continuousMinuteOfDay(bedAt);
        return bedMinute + (durationMin != null ? durationMin / 2 : 0);
    }

    /** 공식 기준이 아니라 제가 임시로 정한 임계값(03:00/05:00) — 팀 리뷰 필요(CLAUDE.md §10). */
    private Chronotype classifyChronotype(int midSleepMinuteOfDay) {
        int clockMinute = midSleepMinuteOfDay % 1440;
        if (clockMinute < 3 * 60) {
            return Chronotype.MORNING;
        }
        if (clockMinute < 5 * 60) {
            return Chronotype.NEUTRAL;
        }
        return Chronotype.EVENING;
    }

    // ---------- 공통 ----------

    private String averageTimeOfDay(List<OffsetDateTime> times) {
        List<OffsetDateTime> present = times.stream().filter(t -> t != null).toList();
        if (present.isEmpty()) {
            return null;
        }
        double avg = present.stream().mapToInt(TimeOfDayMath::continuousMinuteOfDay).average().orElse(0);
        int avgMin = (int) Math.round(avg) % 1440;
        return "%02d:%02d".formatted(avgMin / 60, avgMin % 60);
    }

    private Integer stdDevMinutes(List<OffsetDateTime> times) {
        List<OffsetDateTime> present = times.stream().filter(t -> t != null).toList();
        if (present.size() < 2) {
            return null;
        }
        double mean = present.stream().mapToInt(TimeOfDayMath::continuousMinuteOfDay).average().orElse(0);
        double variance = present.stream().mapToInt(TimeOfDayMath::continuousMinuteOfDay)
                .mapToDouble(m -> Math.pow(m - mean, 2)).average().orElse(0);
        return (int) Math.round(Math.sqrt(variance));
    }

    private Integer diff(Integer current, Integer last) {
        return current == null || last == null ? null : current - last;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
    }
}
