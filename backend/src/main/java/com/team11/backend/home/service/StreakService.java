package com.team11.backend.home.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.TimeOfDayMath;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.home.dto.DailyResultsResponse;
import com.team11.backend.home.dto.DailyResultsResponse.DailyResultView;
import com.team11.backend.home.dto.StreakView;
import com.team11.backend.logs.domain.SleepLog;
import com.team11.backend.logs.domain.SleepLogStatus;
import com.team11.backend.logs.repository.SleepLogRepository;
import com.team11.backend.plans.domain.Plan;
import com.team11.backend.plans.domain.PlanMode;
import com.team11.backend.plans.domain.PlanStatus;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.plans.repository.PlanRepository;
import com.team11.backend.plans.service.PlanService;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스트릭/일별 결과 계산. 매일 06:00 정산 배치 대신 요청마다 SleepLog+Plan에서 즉석으로 롤업한다
 * (Plan도 이미 /plans/tonight 호출 시 즉석 생성하는 방식이라 같은 패턴 — daily_results 테이블 없음).
 */
@Service
@Transactional(readOnly = true)
public class StreakService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final Duration NORMAL_TOLERANCE = Duration.ofMinutes(30);
    private static final Duration FREE_TOLERANCE = Duration.ofMinutes(60);
    private static final Duration AT_RISK_WINDOW = Duration.ofHours(2);
    private static final LocalTime SETTLEMENT_CUTOFF = LocalTime.of(6, 0);
    private static final int FREE_MEDIAN_WINDOW_DAYS = 14;
    private static final int FREE_MEDIAN_MIN_SAMPLES = 5;
    private static final long MAX_RANGE_DAYS = 93;

    private enum Verdict { ACHIEVED, NOT_ACHIEVED, PENDING }

    private record DayResult(Verdict verdict, OffsetDateTime target, OffsetDateTime actualBedAt,
                              Integer deviationMin, Integer durationMin) {
    }

    private final SleepLogRepository sleepLogRepository;
    private final PlanRepository planRepository;
    private final PlanService planService;
    private final UserRepository userRepository;

    public StreakService(SleepLogRepository sleepLogRepository, PlanRepository planRepository,
                          PlanService planService, UserRepository userRepository) {
        this.sleepLogRepository = sleepLogRepository;
        this.planRepository = planRepository;
        this.planService = planService;
        this.userRepository = userRepository;
    }

    /** getTonightPlan()이 Plan을 새로 만들 수 있어 클래스 레벨 readOnly를 여기서만 덮어쓴다. */
    @Transactional
    public StreakView getStreak(Long userId) {
        return buildStreakView(userId, planService.getTonightPlan(userId));
    }

    /** Home에서 이미 조회한 tonightPlan을 재사용 — 같은 요청 안에서 Plan을 두 번 조회/생성하지 않는다. */
    public StreakView getStreak(Long userId, PlanView tonightPlan) {
        return buildStreakView(userId, tonightPlan);
    }

    public record StreakCounts(int currentCount, int bestCount, LocalDate lastAchievedDate) {
    }

    /** 확정된(settled) 날짜까지만 스캔 — "오늘 밤"은 아직 안 끝난 날이라 포함하지 않는다. Plan을 새로 만들지 않는다. */
    public StreakCounts getCurrentAndBest(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
        LocalDate accountStart = user.getCreatedAt().atZone(ASIA_SEOUL).toLocalDate();
        LocalDate today = LocalDate.now(ASIA_SEOUL);
        LocalDate settledThrough = OffsetDateTime.now(ASIA_SEOUL).toLocalTime().isBefore(SETTLEMENT_CUTOFF)
                ? today.minusDays(1) : today;

        int current = 0;
        int best = 0;
        LocalDate lastAchievedDate = null;
        for (LocalDate date = accountStart; !date.isAfter(settledThrough); date = date.plusDays(1)) {
            Verdict verdict = evaluateDay(userId, date).verdict();
            if (verdict == Verdict.ACHIEVED) {
                current++;
                best = Math.max(best, current);
                lastAchievedDate = date;
            } else if (verdict == Verdict.NOT_ACHIEVED) {
                current = 0;
            }
        }
        return new StreakCounts(current, best, lastAchievedDate);
    }

    private StreakView buildStreakView(Long userId, PlanView tonightPlan) {
        StreakCounts counts = getCurrentAndBest(userId);

        OffsetDateTime now = OffsetDateTime.now(ASIA_SEOUL);
        Target tonightTarget = resolveTarget(userId, tonightPlan.targetDate(), tonightPlan.mode(),
                tonightPlan.userBedAt(), tonightPlan.recommendedBedAt());
        OffsetDateTime actualBedAt = sleepLogRepository.findFirstByUserIdAndStatus(userId, SleepLogStatus.OPEN)
                .map(SleepLog::getBedAt)
                .orElse(null);
        boolean todayAchieved = tonightTarget != null && actualBedAt != null
                && Duration.between(tonightTarget.value(), actualBedAt).abs().compareTo(tonightTarget.tolerance()) <= 0;
        boolean atRisk = !todayAchieved && tonightTarget != null
                && (!now.isBefore(tonightTarget.value())
                    || Duration.between(now, tonightTarget.value()).compareTo(AT_RISK_WINDOW) <= 0);

        return new StreakView(counts.currentCount(), counts.bestCount(), counts.lastAchievedDate(), todayAchieved,
                atRisk);
    }

    public DailyResultsResponse getDailyResults(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.DATE_RANGE_TOO_LARGE);
        }

        LocalDate today = LocalDate.now(ASIA_SEOUL);
        List<DailyResultView> results = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DayResult r = evaluateDay(userId, date);
            results.add(new DailyResultView(date, r.verdict() == Verdict.ACHIEVED, r.target(),
                    r.actualBedAt(), r.deviationMin(), r.durationMin(), date.isBefore(today)));
        }
        return new DailyResultsResponse(results);
    }

    private DayResult evaluateDay(Long userId, LocalDate date) {
        Optional<SleepLog> log = earliestClosedLog(userId, date);
        if (log.isEmpty()) {
            return new DayResult(Verdict.NOT_ACHIEVED, null, null, null, null);
        }
        OffsetDateTime actualBedAt = log.get().getBedAt();
        Integer durationMin = log.get().getDurationMin();

        Optional<Plan> plan = planRepository.findByUserIdAndTargetDateAndStatus(userId, date, PlanStatus.ACTIVE);
        if (plan.isEmpty()) {
            return new DayResult(Verdict.PENDING, null, actualBedAt, null, durationMin);
        }

        Target target = resolveTarget(userId, date, plan.get().getMode(), plan.get().getUserBedAt(),
                plan.get().getRecommendedBedAt());
        if (target == null) {
            return new DayResult(Verdict.PENDING, null, actualBedAt, null, durationMin);
        }

        int deviationMin = (int) Duration.between(target.value(), actualBedAt).toMinutes();
        Verdict verdict = Math.abs(deviationMin) <= target.tolerance().toMinutes() ? Verdict.ACHIEVED : Verdict.NOT_ACHIEVED;
        return new DayResult(verdict, target.value(), actualBedAt, deviationMin, durationMin);
    }

    private record Target(OffsetDateTime value, Duration tolerance) {
    }

    /** NORMAL/EXAM은 플랜의 취침 목표 ±30분, FREE는 최근 14일 취침 시각 중앙값 ±60분(데이터 부족하면 null = 판정 보류). */
    private Target resolveTarget(Long userId, LocalDate date, PlanMode mode, OffsetDateTime userBedAt,
                                  OffsetDateTime recommendedBedAt) {
        if (mode == PlanMode.FREE) {
            return medianBedTime(userId, date).map(median -> new Target(median, FREE_TOLERANCE)).orElse(null);
        }
        OffsetDateTime target = userBedAt != null ? userBedAt : recommendedBedAt;
        return new Target(target, NORMAL_TOLERANCE);
    }

    private Optional<SleepLog> earliestClosedLog(Long userId, LocalDate date) {
        return sleepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, date, date).stream()
                .min(Comparator.comparing(SleepLog::getBedAt));
    }

    /** 최근 14일(해당 날짜 전날까지) 취침 시각의 중앙값을 날짜 전날 저녁에 고정한 시각으로 변환. 5건 미만이면 비어있음. */
    private Optional<OffsetDateTime> medianBedTime(Long userId, LocalDate date) {
        LocalDate windowStart = date.minusDays(FREE_MEDIAN_WINDOW_DAYS);
        LocalDate windowEnd = date.minusDays(1);
        List<Integer> continuousMinutes = sleepLogRepository
                .findByUserIdAndDateBetweenOrderByDateDesc(userId, windowStart, windowEnd).stream()
                .map(SleepLog::getBedAt)
                .map(TimeOfDayMath::continuousMinuteOfDay)
                .sorted()
                .toList();
        if (continuousMinutes.size() < FREE_MEDIAN_MIN_SAMPLES) {
            return Optional.empty();
        }

        int mid = continuousMinutes.size() / 2;
        double medianMinutes = continuousMinutes.size() % 2 == 1 ? continuousMinutes.get(mid)
                : (continuousMinutes.get(mid - 1) + continuousMinutes.get(mid)) / 2.0;

        OffsetDateTime base = date.minusDays(1).atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
        return Optional.of(base.plusMinutes(Math.round(medianMinutes)));
    }

}
