package com.team11.backend.plans.service;

import com.team11.backend.ai.AiClient;
import com.team11.backend.ai.dto.AiExamSleepRequest;
import com.team11.backend.ai.dto.AiPlanTonightRequest;
import com.team11.backend.ai.dto.AiPlanTonightRequest.Profile;
import com.team11.backend.ai.dto.AiPlanTonightResult;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.events.domain.Event;
import com.team11.backend.events.domain.EventTag;
import com.team11.backend.events.domain.EventType;
import com.team11.backend.events.dto.EventView;
import com.team11.backend.events.repository.EventRepository;
import com.team11.backend.logs.domain.SleepLogStatus;
import com.team11.backend.logs.dto.SleepLogView;
import com.team11.backend.logs.service.SleepLogService;
import com.team11.backend.plans.domain.FirstEventKind;
import com.team11.backend.plans.domain.Plan;
import com.team11.backend.plans.domain.PlanFeedback;
import com.team11.backend.plans.domain.PlanMode;
import com.team11.backend.plans.domain.PlanStatus;
import com.team11.backend.plans.dto.ExamSleepPlanRequest;
import com.team11.backend.plans.dto.ExamSleepPlanView;
import com.team11.backend.plans.dto.PlanFeedbackRequest;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.plans.repository.PlanFeedbackRepository;
import com.team11.backend.plans.repository.PlanRepository;
import com.team11.backend.timetable.config.SemesterProperties;
import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.timetable.dto.SemesterListResponse.SemesterView;
import com.team11.backend.timetable.dto.TimetableEntryView;
import com.team11.backend.timetable.repository.TimetableEntryRepository;
import com.team11.backend.users.domain.ConsentType;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlanService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalTime TONIGHT_CUTOFF = LocalTime.of(6, 0);
    private static final LocalTime FREE_DEFAULT_WAKE_TIME = LocalTime.of(9, 0);
    private static final int EXAM_EVE_SLEEP_BONUS_MIN = 30;

    private final PlanRepository planRepository;
    private final PlanFeedbackRepository planFeedbackRepository;
    private final UserRepository userRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final EventRepository eventRepository;
    private final SemesterProperties semesterProperties;
    private final AiClient aiClient;
    private final SleepLogService sleepLogService;

    public PlanService(PlanRepository planRepository, PlanFeedbackRepository planFeedbackRepository,
                        UserRepository userRepository, TimetableEntryRepository timetableEntryRepository,
                        EventRepository eventRepository, SemesterProperties semesterProperties, AiClient aiClient,
                        SleepLogService sleepLogService) {
        this.planRepository = planRepository;
        this.planFeedbackRepository = planFeedbackRepository;
        this.userRepository = userRepository;
        this.timetableEntryRepository = timetableEntryRepository;
        this.eventRepository = eventRepository;
        this.semesterProperties = semesterProperties;
        this.aiClient = aiClient;
        this.sleepLogService = sleepLogService;
    }

    /** EXAM-08. AI-03 연동 — 시험 1건 기준 1회성 추천. plans 테이블에 저장하지 않는다(상태 없음, AI 서버와 대칭). */
    public ExamSleepPlanView generateExamSleepPlan(Long userId, ExamSleepPlanRequest request) {
        User user = findUser(userId);
        requireHealthDataConsent(user);

        OffsetDateTime now = OffsetDateTime.now(ASIA_SEOUL);
        Event targetEvent = resolveTargetExam(userId, request.eventId());

        OffsetDateTime horizon = targetEvent != null
                ? Optional.ofNullable(targetEvent.getEndAt()).orElse(targetEvent.getStartAt())
                : now.plusHours(72);
        List<EventView> events = eventRepository.findOverlapping(userId, now.minusDays(7), horizon.plusDays(1))
                .stream().map(EventView::from).limit(500).toList();

        String semesterCode = resolveCurrentSemesterCode(now.toLocalDate());
        List<TimetableEntryView> timetable = semesterCode == null ? List.of()
                : timetableEntryRepository.findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(userId, semesterCode)
                        .stream().map(TimetableEntryView::from).toList();
        List<SemesterView> semesters = semesterCode == null ? List.of()
                : semesterProperties.semesters().stream()
                        .filter(c -> c.code().equals(semesterCode))
                        .map(c -> new SemesterView(c.code(), c.startDate(), c.endDate(), true))
                        .toList();

        List<SleepLogView> sleepLogs = sleepLogService
                .list(userId, now.toLocalDate().minusDays(28), now.toLocalDate(), SleepLogStatus.CLOSED).logs();

        AiExamSleepRequest.UserProfile profile = new AiExamSleepRequest.UserProfile(user.isOnboardingDone(), null,
                new AiExamSleepRequest.Settings(user.getSleepNeedMin(), user.getSleepLatencyMin(),
                        user.getMinSleepMin(), user.getCommuteMin(), user.getPrepMin(), user.getBufferMin(),
                        user.getChronotype()));

        AiExamSleepRequest aiRequest = AiExamSleepRequest.of(now, targetEvent != null ? targetEvent.getId() : null,
                request.preparationState(), request.examNature(), profile, events, timetable, semesters, sleepLogs);

        return ExamSleepPlanView.from(aiClient.examSleepPlan(aiRequest));
    }

    private Event resolveTargetExam(Long userId, Long eventId) {
        if (eventId == null) {
            return null;
        }
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getType() != EventType.EXAM) {
            throw new ApiException(ErrorCode.EVENT_NOT_FOUND);
        }
        if (!event.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return event;
    }

    public PlanView getTonightPlan(Long userId) {
        User user = findUser(userId);
        requireHealthDataConsent(user);

        OffsetDateTime now = OffsetDateTime.now(ASIA_SEOUL);
        LocalDate targetDate = now.toLocalTime().isBefore(TONIGHT_CUTOFF) ? now.toLocalDate() : now.toLocalDate().plusDays(1);

        Plan plan = planRepository.findByUserIdAndTargetDateAndStatus(userId, targetDate, PlanStatus.ACTIVE)
                .orElseGet(() -> generateAndSave(user, targetDate, now));
        return PlanView.from(plan);
    }

    @Transactional(readOnly = true)
    public PlanView getPlanByDate(Long userId, LocalDate date) {
        Plan plan = planRepository.findByUserIdAndTargetDateAndStatus(userId, date, PlanStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.PLAN_NOT_FOUND));
        return PlanView.from(plan);
    }

    /** PLAN-06. 같은 플랜에 다시 보내면 마지막 값으로 덮어쓴다. */
    public void submitFeedback(Long userId, Long planId, PlanFeedbackRequest request) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new ApiException(ErrorCode.PLAN_NOT_FOUND));
        if (!plan.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }

        planFeedbackRepository.findById(planId)
                .ifPresentOrElse(
                        existing -> existing.update(request.rating(), request.followed(), request.reason()),
                        () -> planFeedbackRepository.save(
                                new PlanFeedback(planId, request.rating(), request.followed(), request.reason())));
    }

    private Plan generateAndSave(User user, LocalDate targetDate, OffsetDateTime now) {
        FirstEvent firstEvent = findFirstEvent(user.getId(), targetDate);
        boolean examSoon = hasExamOn(user.getId(), targetDate) || hasExamOn(user.getId(), targetDate.plusDays(1));

        PlanMode mode = firstEvent == null ? PlanMode.FREE : PlanMode.NORMAL;
        int sleepNeedMin = user.getSleepNeedMin() + (examSoon ? EXAM_EVE_SLEEP_BONUS_MIN : 0);

        OffsetDateTime wakeAt = firstEvent != null
                ? firstEvent.startAt().minusMinutes(user.getCommuteMin() + user.getPrepMin() + user.getBufferMin())
                : targetDate.atTime(FREE_DEFAULT_WAKE_TIME).atZone(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime bedAt = wakeAt.minusMinutes(sleepNeedMin + user.getSleepLatencyMin());

        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (firstEvent != null) {
            reasons.add("내일 첫 일정은 %s %s".formatted(firstEvent.startAt().toLocalTime(), firstEvent.title()));
        } else {
            reasons.add("내일은 일정이 없어요. 평소 리듬에 맞춰 잠들면 돼요.");
        }
        reasons.add("수면 필요량 %d시간 %d분 기준".formatted(sleepNeedMin / 60, sleepNeedMin % 60));
        if (examSoon) {
            reasons.add("시험이 있어서 수면을 30분 더 확보했어요.");
        }
        appendDrinkingWarning(user.getId(), targetDate, bedAt, reasons, warnings);

        String generatedBy = "RULE";
        String modelVersion = null;
        Integer breakdownCommuteMin = firstEvent != null ? user.getCommuteMin() : null;
        Integer breakdownPrepMin = firstEvent != null ? user.getPrepMin() : null;
        Integer breakdownBufferMin = firstEvent != null ? user.getBufferMin() : null;
        Integer breakdownSleepNeedMin = firstEvent != null ? sleepNeedMin : null;
        Integer breakdownSleepLatencyMin = firstEvent != null ? user.getSleepLatencyMin() : null;

        Optional<AiPlanTonightResult> aiResult = aiClient.planTonight(buildAiRequest(user, targetDate, now));
        if (aiResult.isPresent()) {
            AiPlanTonightResult.Result r = aiResult.get().result();
            bedAt = r.recommendedBedAt();
            wakeAt = r.recommendedWakeAt();
            sleepNeedMin = r.sleepMin();
            reasons = r.reasons();
            warnings = r.warnings();
            generatedBy = "AI";
            modelVersion = aiResult.get().modelVersion();
            if (r.breakdown() != null) {
                breakdownCommuteMin = r.breakdown().commuteMin();
                breakdownPrepMin = r.breakdown().prepMin();
                breakdownBufferMin = r.breakdown().bufferMin();
                breakdownSleepNeedMin = r.breakdown().sleepNeedMin();
                breakdownSleepLatencyMin = r.breakdown().sleepLatencyMin();
            } else {
                breakdownCommuteMin = breakdownPrepMin = breakdownBufferMin = breakdownSleepNeedMin = breakdownSleepLatencyMin = null;
            }
        }

        Plan plan = new Plan(user.getId(), targetDate, mode, bedAt, wakeAt, sleepNeedMin,
                breakdownCommuteMin, breakdownPrepMin, breakdownBufferMin, breakdownSleepNeedMin, breakdownSleepLatencyMin,
                firstEvent != null ? firstEvent.kind() : null, firstEvent != null ? firstEvent.title() : null,
                firstEvent != null ? firstEvent.startAt() : null,
                reasons, warnings, generatedBy, modelVersion, now);
        return planRepository.save(plan);
    }

    private record FirstEvent(FirstEventKind kind, String title, OffsetDateTime startAt) {
    }

    private FirstEvent findFirstEvent(Long userId, LocalDate targetDate) {
        List<FirstEvent> candidates = new ArrayList<>();

        String semesterCode = resolveCurrentSemesterCode(targetDate);
        if (semesterCode != null) {
            DayOfWeek dayOfWeek = DayOfWeek.values()[targetDate.getDayOfWeek().getValue() - 1];
            for (TimetableEntry entry : timetableEntryRepository
                    .findByUserIdAndSemesterAndDayOfWeek(userId, semesterCode, dayOfWeek)) {
                OffsetDateTime startAt = targetDate.atTime(entry.getStartTime()).atZone(ASIA_SEOUL).toOffsetDateTime();
                candidates.add(new FirstEvent(FirstEventKind.CLASS, entry.getCourseName(), startAt));
            }
        }

        OffsetDateTime dayStart = targetDate.atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime dayEnd = targetDate.plusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        for (Event event : eventRepository.findOverlapping(userId, dayStart, dayEnd)) {
            if (!event.isAllDay()) {
                candidates.add(new FirstEvent(FirstEventKind.EVENT, event.getTitle(), event.getStartAt()));
            }
        }

        return candidates.stream().min(Comparator.comparing(FirstEvent::startAt)).orElse(null);
    }

    private boolean hasExamOn(Long userId, LocalDate date) {
        OffsetDateTime dayStart = date.atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        return eventRepository.findOverlapping(userId, dayStart, dayEnd).stream()
                .anyMatch(e -> e.getType() == EventType.EXAM);
    }

    private void appendDrinkingWarning(Long userId, LocalDate targetDate, OffsetDateTime bedAt,
                                        List<String> reasons, List<String> warnings) {
        OffsetDateTime rangeStart = targetDate.minusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime rangeEnd = targetDate.plusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        eventRepository.findOverlapping(userId, rangeStart, rangeEnd).stream()
                .filter(e -> e.getTag() == EventTag.DRINKING)
                .findFirst()
                .ifPresent(drinking -> {
                    reasons.add("술 약속(%s)이 있어요.".formatted(drinking.getTitle()));
                    OffsetDateTime effectiveEnd = drinking.getEndAt() != null ? drinking.getEndAt() : drinking.getStartAt();
                    if (effectiveEnd.isAfter(bedAt)) {
                        warnings.add("%s에 끝나는 일정이 있어서 취침 권장 시각을 넘길 수 있어요.".formatted(effectiveEnd.toLocalTime()));
                    }
                });
    }

    private AiPlanTonightRequest buildAiRequest(User user, LocalDate targetDate, OffsetDateTime now) {
        String semesterCode = resolveCurrentSemesterCode(targetDate);
        List<TimetableEntryView> timetable = semesterCode == null ? List.of()
                : timetableEntryRepository.findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(user.getId(), semesterCode)
                        .stream().map(TimetableEntryView::from).toList();

        OffsetDateTime rangeStart = now.minusDays(1).toLocalDate().atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime rangeEnd = targetDate.plusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        List<EventView> events = eventRepository.findOverlapping(user.getId(), rangeStart, rangeEnd).stream()
                .map(EventView::from).toList();

        Profile profile = new Profile(user.getChronotype(), user.getSleepNeedMin(), user.getSleepLatencyMin(),
                user.getCommuteMin(), user.getPrepMin(), null, user.getBufferMin(), user.getMinSleepMin());

        return new AiPlanTonightRequest(now, targetDate, profile, List.of(), timetable, events, false);
    }

    private void requireHealthDataConsent(User user) {
        if (!user.hasAgreedTo(ConsentType.HEALTH_DATA)) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
    }

    private String resolveCurrentSemesterCode(LocalDate date) {
        return semesterProperties.resolveCodeFor(date);
    }
}
