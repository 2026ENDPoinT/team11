package com.team11.backend.calendar.service;

import com.team11.backend.calendar.dto.DaySummaryResponse;
import com.team11.backend.calendar.dto.MonthSummaryResponse;
import com.team11.backend.calendar.dto.MonthSummaryResponse.DaySummary;
import com.team11.backend.calendar.dto.MonthSummaryResponse.EventSummary;
import com.team11.backend.calendar.dto.MonthSummaryResponse.PlanSummary;
import com.team11.backend.calendar.dto.MonthSummaryResponse.SleepSummary;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.events.domain.Event;
import com.team11.backend.events.dto.EventView;
import com.team11.backend.events.repository.EventRepository;
import com.team11.backend.home.dto.DailyResultsResponse.DailyResultView;
import com.team11.backend.home.service.StreakService;
import com.team11.backend.logs.dto.PrepLogView;
import com.team11.backend.logs.dto.SleepLogView;
import com.team11.backend.logs.repository.PrepLogRepository;
import com.team11.backend.logs.repository.SleepLogRepository;
import com.team11.backend.plans.domain.Plan;
import com.team11.backend.plans.domain.PlanStatus;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.plans.repository.PlanRepository;
import com.team11.backend.timetable.config.SemesterProperties;
import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.timetable.dto.TimetableEntryView;
import com.team11.backend.timetable.repository.TimetableEntryRepository;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CalendarService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

    private final TimetableEntryRepository timetableEntryRepository;
    private final EventRepository eventRepository;
    private final PlanRepository planRepository;
    private final SleepLogRepository sleepLogRepository;
    private final PrepLogRepository prepLogRepository;
    private final SemesterProperties semesterProperties;
    private final StreakService streakService;

    public CalendarService(TimetableEntryRepository timetableEntryRepository, EventRepository eventRepository,
                            PlanRepository planRepository, SleepLogRepository sleepLogRepository,
                            PrepLogRepository prepLogRepository, SemesterProperties semesterProperties,
                            StreakService streakService) {
        this.timetableEntryRepository = timetableEntryRepository;
        this.eventRepository = eventRepository;
        this.planRepository = planRepository;
        this.sleepLogRepository = sleepLogRepository;
        this.prepLogRepository = prepLogRepository;
        this.semesterProperties = semesterProperties;
        this.streakService = streakService;
    }

    public MonthSummaryResponse getMonthSummary(Long userId, String month) {
        YearMonth yearMonth = parseYearMonth(month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        Map<DayOfWeek, List<TimetableEntry>> entriesByDayOfWeek = entriesByDayOfWeek(userId, monthStart);
        Map<LocalDate, List<Event>> eventsByDate = eventsByDate(userId, monthStart, monthEnd);
        Map<LocalDate, Plan> planByDate = planRepository
                .findByUserIdAndTargetDateBetweenAndStatus(userId, monthStart, monthEnd, PlanStatus.ACTIVE).stream()
                .collect(java.util.stream.Collectors.toMap(Plan::getTargetDate, p -> p));
        Map<LocalDate, DailyResultView> resultByDate = streakService.getDailyResults(userId, monthStart, monthEnd)
                .results().stream()
                .collect(java.util.stream.Collectors.toMap(DailyResultView::date, r -> r));

        List<DaySummary> days = new ArrayList<>();
        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            DayOfWeek dayOfWeek = DayOfWeek.values()[date.getDayOfWeek().getValue() - 1];
            List<TimetableEntry> classes = entriesByDayOfWeek.getOrDefault(dayOfWeek, List.of());
            LocalTime firstClassTime = classes.stream().map(TimetableEntry::getStartTime)
                    .min(Comparator.naturalOrder()).orElse(null);

            List<EventSummary> events = eventsByDate.getOrDefault(date, List.of()).stream()
                    .map(e -> new EventSummary(e.getId(), e.getType(), e.getTitle(), e.getTag()))
                    .toList();

            Plan plan = planByDate.get(date);
            PlanSummary planSummary = plan == null ? null
                    : new PlanSummary(plan.getMode(), plan.getRecommendedBedAt(), plan.getRecommendedWakeAt());

            DailyResultView result = resultByDate.get(date);
            SleepSummary sleepSummary = result == null || result.actualBedAt() == null ? null
                    : new SleepSummary(result.durationMin(), result.goalMet());

            days.add(new DaySummary(date, classes.size(), firstClassTime, events, planSummary, sleepSummary));
        }

        return new MonthSummaryResponse(month, days);
    }

    public DaySummaryResponse getDaySummary(Long userId, String dateText) {
        LocalDate date = parseDate(dateText);

        DayOfWeek dayOfWeek = DayOfWeek.values()[date.getDayOfWeek().getValue() - 1];
        List<TimetableEntryView> classes = entriesByDayOfWeek(userId, date).getOrDefault(dayOfWeek, List.of())
                .stream().map(TimetableEntryView::from).toList();

        OffsetDateTime dayStart = date.atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        List<EventView> events = eventRepository.findOverlapping(userId, dayStart, dayEnd).stream()
                .map(EventView::from).toList();

        PlanView plan = planRepository.findByUserIdAndTargetDateAndStatus(userId, date, PlanStatus.ACTIVE)
                .map(PlanView::from).orElse(null);

        SleepLogView sleepLog = sleepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, date, date)
                .stream()
                .max(Comparator.comparing(log -> log.getDurationMin() == null ? -1 : log.getDurationMin()))
                .map(SleepLogView::from).orElse(null);

        PrepLogView prepLog = prepLogRepository.findByUserIdAndDate(userId, date).map(PrepLogView::from).orElse(null);

        return new DaySummaryResponse(date, classes, events, plan, sleepLog, prepLog, List.of());
    }

    private Map<DayOfWeek, List<TimetableEntry>> entriesByDayOfWeek(Long userId, LocalDate anyDateInRange) {
        String semesterCode = semesterProperties.resolveCodeFor(anyDateInRange);
        if (semesterCode == null) {
            return new EnumMap<>(DayOfWeek.class);
        }
        return timetableEntryRepository.findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(userId, semesterCode)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(TimetableEntry::getDayOfWeek));
    }

    /** ponytail: 여러 날에 걸친 긴 일정은 시작일에만 배정(종료일까지 펼치는 건 필요해지면 추가). */
    private Map<LocalDate, List<Event>> eventsByDate(Long userId, LocalDate monthStart, LocalDate monthEnd) {
        OffsetDateTime rangeStart = monthStart.atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime rangeEnd = monthEnd.plusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        Map<LocalDate, List<Event>> byDate = new HashMap<>();
        for (Event event : eventRepository.findOverlapping(userId, rangeStart, rangeEnd)) {
            LocalDate date = event.getStartAt().toLocalDate();
            if (!date.isBefore(monthStart) && !date.isAfter(monthEnd)) {
                byDate.computeIfAbsent(date, d -> new ArrayList<>()).add(event);
            }
        }
        return byDate;
    }

    private YearMonth parseYearMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
