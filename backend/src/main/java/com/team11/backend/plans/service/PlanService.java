package com.team11.backend.plans.service;

import com.team11.backend.ai.AiClient;
import com.team11.backend.ai.AiRejectedException;
import com.team11.backend.ai.dto.AiExamSleepRequest;
import com.team11.backend.ai.dto.AiExamSleepResult;
import com.team11.backend.ai.dto.AiFocusSleepRequest;
import com.team11.backend.ai.dto.AiFocusSleepResult;
import com.team11.backend.ai.dto.AiPlanTonightRequest;
import com.team11.backend.ai.dto.AiPlanTonightResult;
import com.team11.backend.ai.dto.AiSnapshot;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.Kst;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.events.domain.Event;
import com.team11.backend.events.domain.EventType;
import com.team11.backend.events.service.EventService;
import com.team11.backend.logs.domain.SleepLog;
import com.team11.backend.logs.service.SleepLogService;
import com.team11.backend.plans.domain.Plan;
import com.team11.backend.plans.domain.PlanDraft;
import com.team11.backend.plans.domain.PlanMode;
import com.team11.backend.plans.domain.PlanStatus;
import com.team11.backend.plans.dto.GeneratePlanRequest;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.plans.repository.PlanRepository;
import com.team11.backend.plans.service.RulePlanner.FirstEvent;
import com.team11.backend.plans.service.RulePlanner.Profile;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.timetable.service.TimetableService;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 3모드 플랜. 저장은 (userId, date, mode) 1건, GET은 있으면 그대로, POST는 다시 만들어 덮어쓴다. */
@Service
@Transactional
public class PlanService {

    /** 이 시각 전이면 "오늘 밤"의 기상일은 오늘(새벽에 보는 경우), 아니면 내일. */
    static final LocalTime TONIGHT_CUTOFF = LocalTime.of(6, 0);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int TONIGHT_HISTORY_DAYS = 35;
    private static final int EXAM_HISTORY_DAYS = 28;
    private static final int FOCUS_HISTORY_DAYS = 90;
    private static final int FOCUS_MIN_HISTORY = 3;
    private static final int FOCUS_MAX_HISTORY = 90;
    private static final Duration EXAM_LOOKAHEAD = Duration.ofHours(72);

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final TimetableService timetableService;
    private final EventService eventService;
    private final SleepLogService sleepLogService;
    private final AiClient aiClient;

    public PlanService(PlanRepository planRepository, UserRepository userRepository, TimetableService timetableService,
                        EventService eventService, SleepLogService sleepLogService, AiClient aiClient) {
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.timetableService = timetableService;
        this.eventService = eventService;
        this.sleepLogService = sleepLogService;
        this.aiClient = aiClient;
    }

    public PlanView getToday(Long userId, PlanMode mode) {
        User user = findUser(userId);
        OffsetDateTime now = Kst.now();
        LocalDate date = targetDate(now);
        Plan plan = planRepository.findByUserIdAndDateAndMode(userId, date, mode)
                .orElseGet(() -> planRepository.save(new Plan(userId, date, mode,
                        generate(user, mode, date, now, GeneratePlanRequest.EMPTY))));
        return PlanView.from(plan);
    }

    public PlanView regenerateToday(Long userId, PlanMode mode, GeneratePlanRequest request) {
        User user = findUser(userId);
        OffsetDateTime now = Kst.now();
        LocalDate date = targetDate(now);
        PlanDraft draft = generate(user, mode, date, now, request == null ? GeneratePlanRequest.EMPTY : request);
        Plan plan = planRepository.findByUserIdAndDateAndMode(userId, date, mode)
                .map(existing -> {
                    existing.replace(draft);
                    return existing;
                })
                .orElseGet(() -> planRepository.save(new Plan(userId, date, mode, draft)));
        return PlanView.from(plan);
    }

    public static LocalDate targetDate(OffsetDateTime now) {
        LocalDate today = Kst.dateOf(now);
        return Kst.timeOf(now).isBefore(TONIGHT_CUTOFF) ? today : today.plusDays(1);
    }

    private PlanDraft generate(User user, PlanMode mode, LocalDate date, OffsetDateTime now, GeneratePlanRequest request) {
        return switch (mode) {
            case NORMAL -> generateNormal(user, date, now);
            case FOCUS -> generateFocus(user, date, now);
            case EXAM -> generateExam(user, date, now, request);
        };
    }

    // ---- NORMAL (AI-02, 실패 시 규칙 폴백) ----

    private PlanDraft generateNormal(User user, LocalDate date, OffsetDateTime now) {
        FirstEvent firstEvent = firstEventOn(user.getId(), date);
        Profile profile = profileOf(user);

        LocalDate historyFrom = Kst.dateOf(now).minusDays(TONIGHT_HISTORY_DAYS);
        List<AiSnapshot.SleepLog> history = sleepLogService.between(user.getId(), historyFrom, Kst.dateOf(now)).stream()
                .filter(l -> l.getWakeAt().isBefore(now))
                .map(PlanService::toAiSleepLog).toList();
        List<AiSnapshot.TimetableEntry> timetable = timetableService.entriesForSemesterOf(user.getId(), date).stream()
                .map(PlanService::toAiTimetable).toList();
        List<AiSnapshot.Event> events = eventService
                .between(user.getId(), Kst.startOfDay(date.minusDays(1)), Kst.startOfDay(date.plusDays(1))).stream()
                .map(PlanService::toAiEvent).toList();

        AiPlanTonightRequest aiRequest = new AiPlanTonightRequest(now, date,
                new AiPlanTonightRequest.Profile(user.getSleepNeedMin(), user.getSleepLatencyMin(), user.getCommuteMin(),
                        user.getPrepMin(), user.getBufferMin(), user.getMinSleepMin()),
                history, timetable, events, false);

        Optional<AiPlanTonightResult> ai = aiClient.planTonight(aiRequest);
        if (ai.isEmpty() || ai.get().result() == null) {
            return RulePlanner.plan(now, date, firstEvent, profile);
        }
        AiPlanTonightResult.Result r = ai.get().result();
        return new PlanDraft(PlanStatus.READY, r.recommendedBedAt(), r.recommendedWakeAt(), r.sleepMin(),
                firstEvent != null ? firstEvent.basisText() : null, PlanDraft.AI, ai.get().modelVersion(),
                r.reasons(), r.warnings());
    }

    // ---- FOCUS (AI-06, 대체 규칙 없음) ----

    private PlanDraft generateFocus(User user, LocalDate date, OffsetDateTime now) {
        LocalDate today = Kst.dateOf(now);
        List<SleepLog> logs = sleepLogService.between(user.getId(), today.minusDays(FOCUS_HISTORY_DAYS), today).stream()
                .filter(l -> l.getWakeAt().isBefore(now)).toList();
        if (logs.size() < FOCUS_MIN_HISTORY) {
            throw new ApiException(ErrorCode.SLEEP_HISTORY_INSUFFICIENT,
                    java.util.Map.of("required", FOCUS_MIN_HISTORY, "actual", logs.size()));
        }
        // 수동 기록은 침대 체류시간이라 실수면 = 체류 − 사용자 입면시간으로 추정한다(ponytail: 웨어러블 생기면 실측값 사용).
        int latency = user.getSleepLatencyMin();
        List<AiFocusSleepRequest.History> history = logs.subList(Math.max(0, logs.size() - FOCUS_MAX_HISTORY), logs.size())
                .stream()
                .map(l -> new AiFocusSleepRequest.History(l.getDate(), Kst.timeOf(l.getBedAt()).format(HH_MM),
                        Kst.timeOf(l.getWakeAt()).format(HH_MM), Math.max(1, l.getDurationMin() - latency), latency))
                .toList();

        FirstEvent firstEvent = firstEventOn(user.getId(), date);
        OffsetDateTime wakeDeadline = RulePlanner.wakeDeadline(date, firstEvent, profileOf(user));
        String basisText = firstEvent != null ? firstEvent.basisText() : null;

        AiFocusSleepRequest request = new AiFocusSleepRequest(AiFocusSleepRequest.CONTRACT_VERSION, now,
                AiFocusSleepRequest.TIMEZONE, wakeDeadline, user.getFocusMinInBedMin(), user.getFocusMaxInBedMin(), history);
        AiFocusSleepResult ai;
        try {
            ai = aiClient.focusSleepPlan(request);
        } catch (AiRejectedException e) {
            // UNSUPPORTED_HORIZON(마감이 지났거나 36시간 초과) / NO_FEASIBLE_PLAN(범위·마감 동시 충족 불가)
            return PlanDraft.infeasible(basisText, PlanDraft.AI, null, List.of(e.getMessage()));
        }
        AiFocusSleepResult.Recommendation rec = ai.result() == null ? null : ai.result().recommendation();
        if (rec == null) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        int sleepMin = (int) Math.round(rec.estimatedActualSleepHours() * 60);
        List<String> reasons = new ArrayList<>();
        reasons.add("침대에 %s 머물면 약 %s 잘 수 있을 것으로 예상돼요.".formatted(hours(rec.sleepOpportunityHours()),
                hours(rec.estimatedActualSleepHours())));
        if (firstEvent != null) {
            reasons.add("첫 일정(%s) 전 준비·통학 시간을 뺀 기상 마감에 맞췄어요.".formatted(firstEvent.basisText()));
        }
        List<String> warnings = new ArrayList<>();
        if (ai.result().cycleFallback() != null && ai.result().cycleFallback().used()) {
            warnings.add("예측 가능한 범위를 벗어나 90분 수면 주기 정렬로 추천했어요.");
        }
        return new PlanDraft(PlanStatus.READY, rec.bedtime(), rec.wakeTime(), sleepMin, basisText, PlanDraft.AI,
                ai.modelVersion(), reasons, warnings);
    }

    // ---- EXAM (AI-03, 대체 규칙 없음) ----

    private PlanDraft generateExam(User user, LocalDate date, OffsetDateTime now, GeneratePlanRequest request) {
        if (request.preparationState() == null) {
            throw ApiException.invalid("preparationState", "시험 모드에는 공부량 선택이 필요해요.");
        }
        if (request.examNature() == null) {
            throw ApiException.invalid("examNature", "시험 모드에는 시험 유형 선택이 필요해요.");
        }
        Event targetExam = resolveTargetExam(user.getId(), request.eventId(), now);
        String basisText = targetExam == null ? null
                : new FirstEvent(targetExam.getTitle(), targetExam.getStartAt()).basisText();

        OffsetDateTime horizon = now.plus(EXAM_LOOKAHEAD).plusDays(1);
        List<AiSnapshot.Event> events = eventService.between(user.getId(), now.minusDays(7), horizon).stream()
                .map(PlanService::toAiEvent).limit(500).toList();
        List<TimetableEntry> entries = timetableService.entriesForSemesterOf(user.getId(), Kst.dateOf(now));
        List<AiSnapshot.TimetableEntry> timetable = entries.stream().map(PlanService::toAiTimetable).limit(100).toList();
        List<AiSnapshot.Semester> semesters = timetableService.semesters().containing(Kst.dateOf(now))
                .map(c -> List.of(new AiSnapshot.Semester(c.code(), c.startDate(), c.endDate(), true)))
                .orElse(List.of());
        LocalDate today = Kst.dateOf(now);
        List<AiSnapshot.SleepLog> sleepLogs = sleepLogService.between(user.getId(), today.minusDays(EXAM_HISTORY_DAYS), today)
                .stream().filter(l -> l.getWakeAt().isBefore(now)).map(PlanService::toAiSleepLog).limit(200).toList();

        AiExamSleepRequest aiRequest = new AiExamSleepRequest(AiExamSleepRequest.CONTRACT_VERSION, now,
                now.toEpochSecond(), targetExam != null ? targetExam.getId() : null,
                new AiExamSleepRequest.Selection(request.preparationState(), request.examNature(), now),
                new AiExamSleepRequest.UserProfile(user.isOnboardingDone(),
                        new AiExamSleepRequest.Settings(user.getSleepNeedMin(), user.getSleepLatencyMin(),
                                user.getMinSleepMin(), user.getCommuteMin(), user.getPrepMin(), user.getBufferMin())),
                events, timetable, semesters, sleepLogs);

        AiExamSleepResult ai;
        try {
            ai = aiClient.examSleepPlan(aiRequest);
        } catch (AiRejectedException e) {
            throw new ApiException(ErrorCode.EVENT_NOT_FOUND, java.util.Map.of("reason", e.getMessage()));
        }
        AiExamSleepResult.Result r = ai.result();
        if (r == null) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        PlanStatus status = parseStatus(r.status());
        AiExamSleepResult.TimelineEntry mainSleep = r.selected() == null || r.selected().timeline() == null ? null
                : r.selected().timeline().stream()
                        .filter(t -> "MAIN_SLEEP".equals(t.kind()))
                        .min(Comparator.comparing(AiExamSleepResult.TimelineEntry::startAt)).orElse(null);
        if (status == PlanStatus.NO_FEASIBLE_PLAN || mainSleep == null) {
            return PlanDraft.infeasible(basisText, PlanDraft.AI, ai.modelVersion(),
                    r.warnings() != null ? r.warnings() : List.of());
        }
        int opportunity = (int) Duration.between(mainSleep.startAt(), mainSleep.endAt()).toMinutes();
        return new PlanDraft(status, mainSleep.startAt(), mainSleep.endAt(),
                Math.max(0, opportunity - user.getSleepLatencyMin()), basisText, PlanDraft.AI, ai.modelVersion(),
                r.reasons(), r.warnings());
    }

    /** eventId가 있으면 본인 소유의 EXAM이어야 하고, 없으면 72시간 내 가장 가까운 시험(AI와 같은 규칙, basisText용). */
    private Event resolveTargetExam(Long userId, Long eventId, OffsetDateTime now) {
        if (eventId != null) {
            Event event = eventService.findOwned(userId, eventId);
            if (event.getType() != EventType.EXAM) {
                throw new ApiException(ErrorCode.EVENT_NOT_FOUND);
            }
            return event;
        }
        return eventService.between(userId, now, now.plus(EXAM_LOOKAHEAD)).stream()
                .filter(e -> e.getType() == EventType.EXAM && !e.isAllDay() && e.getStartAt().isAfter(now))
                .min(Comparator.comparing(Event::getStartAt)).orElse(null);
    }

    private static PlanStatus parseStatus(String status) {
        try {
            return status == null ? PlanStatus.READY : PlanStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return PlanStatus.READY;
        }
    }

    // ---- 공통 ----

    /** 기상일의 가장 이른 수업 또는 종일 아닌 일정. */
    private FirstEvent firstEventOn(Long userId, LocalDate date) {
        List<FirstEvent> candidates = new ArrayList<>();
        for (TimetableEntry entry : timetableService.entriesOn(userId, date)) {
            candidates.add(new FirstEvent(entry.getCourseName(), Kst.at(date, entry.getStartTime())));
        }
        OffsetDateTime dayStart = Kst.startOfDay(date);
        OffsetDateTime dayEnd = Kst.startOfDay(date.plusDays(1));
        for (Event event : eventService.between(userId, dayStart, dayEnd)) {
            if (!event.isAllDay() && !event.getStartAt().isBefore(dayStart) && event.getStartAt().isBefore(dayEnd)) {
                candidates.add(new FirstEvent(event.getTitle(), event.getStartAt()));
            }
        }
        return candidates.stream().min(Comparator.comparing(FirstEvent::startAt)).orElse(null);
    }

    private static Profile profileOf(User u) {
        return new Profile(u.getSleepNeedMin(), u.getSleepLatencyMin(), u.getCommuteMin(), u.getPrepMin(), u.getBufferMin());
    }

    private static AiSnapshot.Event toAiEvent(Event e) {
        return new AiSnapshot.Event(e.getId(), e.getType().aiName(), e.getStartAt(), e.getEndAt(), e.isAllDay());
    }

    private static AiSnapshot.TimetableEntry toAiTimetable(TimetableEntry t) {
        return new AiSnapshot.TimetableEntry(t.getId(), t.getDayOfWeek().name(), t.getStartTime().format(HH_MM),
                t.getEndTime().format(HH_MM), t.getSemester(), t.getCourseName());
    }

    /** 앱 컨디션 1~3 → AI 품질 1~5(1→1, 2→3, 3→5). 수동 기록이라 status=CLOSED, source=MANUAL 고정. */
    private static AiSnapshot.SleepLog toAiSleepLog(SleepLog l) {
        Integer quality = l.getQuality() == null ? null : l.getQuality() * 2 - 1;
        return new AiSnapshot.SleepLog(l.getId(), l.getDate(), l.getBedAt(), l.getWakeAt(), l.getDurationMin(), quality,
                "CLOSED", "MANUAL");
    }

    private static String hours(double h) {
        int total = (int) Math.round(h * 60);
        return total % 60 == 0 ? "%d시간".formatted(total / 60) : "%d시간 %d분".formatted(total / 60, total % 60);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
    }
}
