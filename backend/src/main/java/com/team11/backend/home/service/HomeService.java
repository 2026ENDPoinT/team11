package com.team11.backend.home.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.events.domain.Event;
import com.team11.backend.events.domain.EventType;
import com.team11.backend.events.repository.EventRepository;
import com.team11.backend.home.dto.HomeResponse;
import com.team11.backend.home.dto.HomeResponse.BannerView;
import com.team11.backend.home.dto.HomeResponse.HomeStreakView;
import com.team11.backend.home.dto.HomeResponse.NextEventView;
import com.team11.backend.home.dto.HomeResponse.OpenSleepLogView;
import com.team11.backend.home.dto.HomeResponse.TipView;
import com.team11.backend.home.dto.HomeResponse.TonightView;
import com.team11.backend.home.dto.StreakView;
import com.team11.backend.logs.domain.SleepLogStatus;
import com.team11.backend.logs.repository.SleepLogRepository;
import com.team11.backend.notifications.repository.NotificationRepository;
import com.team11.backend.notifications.service.NotificationService;
import com.team11.backend.plans.domain.FirstEventKind;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.plans.service.PlanService;
import com.team11.backend.timetable.config.SemesterProperties;
import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.timetable.repository.TimetableEntryRepository;
import com.team11.backend.tips.service.TipService;
import com.team11.backend.users.domain.ConsentType;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.dto.CharacterView;
import com.team11.backend.users.repository.UserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class HomeService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final int NEXT_EVENTS_LOOKAHEAD_DAYS = 14;
    private static final int NEXT_EVENTS_LIMIT = 5;
    private static final int EXAM_SOON_LOOKAHEAD_DAYS = 2;
    private static final int LATE_NIGHT_HOUR = 5;

    private final PlanService planService;
    private final StreakService streakService;
    private final UserRepository userRepository;
    private final SleepLogRepository sleepLogRepository;
    private final EventRepository eventRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final SemesterProperties semesterProperties;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final TipService tipService;

    public HomeService(PlanService planService, StreakService streakService, UserRepository userRepository,
                        SleepLogRepository sleepLogRepository, EventRepository eventRepository,
                        TimetableEntryRepository timetableEntryRepository, SemesterProperties semesterProperties,
                        NotificationRepository notificationRepository, NotificationService notificationService,
                        TipService tipService) {
        this.planService = planService;
        this.streakService = streakService;
        this.userRepository = userRepository;
        this.sleepLogRepository = sleepLogRepository;
        this.eventRepository = eventRepository;
        this.timetableEntryRepository = timetableEntryRepository;
        this.semesterProperties = semesterProperties;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.tipService = tipService;
    }

    public HomeResponse getHome(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
        if (!user.hasAgreedTo(ConsentType.HEALTH_DATA)) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED);
        }

        OffsetDateTime now = OffsetDateTime.now(ASIA_SEOUL);
        PlanView tonightPlan = planService.getTonightPlan(userId);
        StreakView streak = streakService.getStreak(userId, tonightPlan);

        TonightView tonight = toTonightView(tonightPlan, now);
        List<NextEventView> nextEvents = findNextEvents(userId, now);
        OpenSleepLogView openSleepLog = sleepLogRepository.findFirstByUserIdAndStatus(userId, SleepLogStatus.OPEN)
                .map(log -> new OpenSleepLogView(log.getId(), log.getBedAt()))
                .orElse(null);
        List<BannerView> banners;
        if (streak.atRisk()) {
            notificationService.ensureStreakRiskNotification(userId, streak.currentCount());
            banners = List.of(new BannerView("STREAK_RISK", "오늘 스트릭이 끊길 위험이 있어요.", null));
        } else {
            banners = List.of();
        }
        long unreadNotifications = notificationRepository.countByUserIdAndReadAtIsNull(userId);

        TipService.Tip tip = tipService.getTodayTip(userId, hasExamSoon(userId, now), isLateNight(tonightPlan));

        return new HomeResponse(now, tonight, nextEvents,
                new HomeStreakView(streak.currentCount(), streak.bestCount(), streak.atRisk()),
                CharacterView.from(user.getCharacter()), openSleepLog, null, (int) unreadNotifications, banners,
                new TipView(tip.id(), tip.text()));
    }

    /** 홈 캐릭터 말풍선 팁 선택용 — 모레까지 시험 일정이 있으면 "시험기간" 상황으로 본다. */
    private boolean hasExamSoon(Long userId, OffsetDateTime now) {
        OffsetDateTime rangeEnd = now.plusDays(EXAM_SOON_LOOKAHEAD_DAYS);
        return eventRepository.findOverlapping(userId, now, rangeEnd).stream()
                .anyMatch(event -> event.getType() == EventType.EXAM);
    }

    /** 취침 권장(또는 사용자 지정) 시각이 자정 넘어 새벽이면 "늦은 일정" 상황으로 본다. */
    private boolean isLateNight(PlanView plan) {
        OffsetDateTime bedAt = plan.userBedAt() != null ? plan.userBedAt() : plan.recommendedBedAt();
        return bedAt.getHour() < LATE_NIGHT_HOUR;
    }

    private TonightView toTonightView(PlanView plan, OffsetDateTime now) {
        OffsetDateTime target = plan.userBedAt() != null ? plan.userBedAt() : plan.recommendedBedAt();
        long minutesUntilBed = java.time.Duration.between(now, target).toMinutes();
        return new TonightView(plan.planId(), plan.targetDate(), plan.mode(), plan.recommendedBedAt(),
                plan.userBedAt(), plan.recommendedWakeAt(), minutesUntilBed, plan.reasons(), plan.warnings());
    }

    /** 현재 시각 이후 가장 가까운 수업/일정 최대 5개. 14일 범위만 훑는다(ponytail: 고정 lookahead, 필요하면 늘림). */
    private List<NextEventView> findNextEvents(Long userId, OffsetDateTime now) {
        List<NextEventView> candidates = new ArrayList<>();
        LocalDate today = now.toLocalDate();
        LocalDate endDate = today.plusDays(NEXT_EVENTS_LOOKAHEAD_DAYS);

        String semesterCode = semesterProperties.resolveCodeFor(today);
        if (semesterCode != null) {
            List<TimetableEntry> entries = timetableEntryRepository
                    .findByUserIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(userId, semesterCode);
            for (LocalDate date = today; !date.isAfter(endDate); date = date.plusDays(1)) {
                DayOfWeek dayOfWeek = DayOfWeek.values()[date.getDayOfWeek().getValue() - 1];
                for (TimetableEntry entry : entries) {
                    if (entry.getDayOfWeek() != dayOfWeek) {
                        continue;
                    }
                    OffsetDateTime startAt = date.atTime(entry.getStartTime()).atZone(ASIA_SEOUL).toOffsetDateTime();
                    if (!startAt.isBefore(now)) {
                        candidates.add(new NextEventView(FirstEventKind.CLASS, entry.getCourseName(), startAt));
                    }
                }
            }
        }

        OffsetDateTime rangeEnd = endDate.plusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        for (Event event : eventRepository.findOverlapping(userId, now, rangeEnd)) {
            if (!event.isAllDay() && !event.getStartAt().isBefore(now)) {
                candidates.add(new NextEventView(FirstEventKind.EVENT, event.getTitle(), event.getStartAt()));
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparing(NextEventView::startAt))
                .limit(NEXT_EVENTS_LIMIT)
                .toList();
    }
}
