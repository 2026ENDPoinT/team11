package com.team11.backend.logs.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.logs.domain.SleepLog;
import com.team11.backend.logs.domain.SleepLogSource;
import com.team11.backend.logs.domain.SleepLogStatus;
import com.team11.backend.logs.dto.CreateSleepLogRequest;
import com.team11.backend.logs.dto.EndSleepLogRequest;
import com.team11.backend.logs.dto.EndSleepLogResponse;
import com.team11.backend.logs.dto.SleepLogListResponse;
import com.team11.backend.logs.dto.SleepLogListResponse.Summary;
import com.team11.backend.logs.dto.SleepLogView;
import com.team11.backend.logs.dto.StartSleepLogRequest;
import com.team11.backend.logs.dto.UpdateSleepLogRequest;
import com.team11.backend.logs.repository.SleepLogRepository;
import com.team11.backend.plans.domain.Plan;
import com.team11.backend.plans.domain.PlanStatus;
import com.team11.backend.plans.repository.PlanRepository;
import com.team11.backend.users.domain.ConsentType;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SleepLogService {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    private static final Duration MAX_FUTURE_START = Duration.ofMinutes(5);
    private static final Duration MAX_SLEEP_DURATION = Duration.ofHours(24);
    private static final Duration GOAL_MET_TOLERANCE = Duration.ofMinutes(30);
    private static final long MAX_RANGE_DAYS = 93;

    private final SleepLogRepository sleepLogRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;

    public SleepLogService(SleepLogRepository sleepLogRepository, UserRepository userRepository,
                            PlanRepository planRepository) {
        this.sleepLogRepository = sleepLogRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
    }

    public SleepLogView start(Long userId, StartSleepLogRequest request) {
        User user = findUserWithConsent(userId);
        OffsetDateTime at = request.at() != null ? request.at() : OffsetDateTime.now(ASIA_SEOUL);
        if (at.isAfter(OffsetDateTime.now(ASIA_SEOUL).plus(MAX_FUTURE_START))) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }

        sleepLogRepository.findFirstByUserIdAndStatus(userId, SleepLogStatus.OPEN).ifPresent(open -> {
            throw new ApiException(ErrorCode.SLEEP_LOG_ALREADY_OPEN,
                    Map.of("sleepLogId", open.getId(), "bedAt", open.getBedAt().toString()));
        });
        requireNoClosedOverlap(userId, at, at, null);

        SleepLog log = sleepLogRepository.save(new SleepLog(userId, at, SleepLogSource.TAP));
        return SleepLogView.from(log);
    }

    public EndSleepLogResponse end(Long userId, EndSleepLogRequest request) {
        findUserWithConsent(userId);
        SleepLog open = sleepLogRepository.findFirstByUserIdAndStatus(userId, SleepLogStatus.OPEN)
                .orElseThrow(() -> new ApiException(ErrorCode.SLEEP_LOG_NOT_OPEN));

        OffsetDateTime at = request.at() != null ? request.at() : OffsetDateTime.now(ASIA_SEOUL);
        if (!at.isAfter(open.getBedAt()) || Duration.between(open.getBedAt(), at).compareTo(MAX_SLEEP_DURATION) > 0) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }

        LocalDate date = at.toLocalDate();
        open.close(at, request.quality(), date);

        boolean goalMet = planRepository.findByUserIdAndTargetDateAndStatus(userId, date, PlanStatus.ACTIVE)
                .map(plan -> isWithinGoal(open.getBedAt(), plan))
                .orElse(false);

        return EndSleepLogResponse.of(SleepLogView.from(open), goalMet);
    }

    @Transactional(readOnly = true)
    public SleepLogView getOpen(Long userId) {
        return sleepLogRepository.findFirstByUserIdAndStatus(userId, SleepLogStatus.OPEN)
                .map(SleepLogView::from)
                .orElse(null);
    }

    public SleepLogView create(Long userId, CreateSleepLogRequest request) {
        findUserWithConsent(userId);
        validateClosedRange(request.bedAt(), request.wakeAt());
        requireNoClosedOverlap(userId, request.bedAt(), request.wakeAt(), null);

        SleepLog log = new SleepLog(userId, request.bedAt(), request.wakeAt(), request.quality(),
                request.wakeAt().toLocalDate(), SleepLogSource.MANUAL);
        sleepLogRepository.save(log);
        return SleepLogView.from(log);
    }

    @Transactional(readOnly = true)
    public SleepLogListResponse list(Long userId, LocalDate from, LocalDate to, SleepLogStatus status) {
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.DATE_RANGE_TOO_LARGE);
        }
        List<SleepLog> logs = sleepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, from, to).stream()
                .filter(l -> status == null || l.getStatus() == status)
                .toList();

        Integer avgDurationMin = logs.isEmpty() ? null
                : (int) logs.stream().mapToInt(SleepLog::getDurationMin).average().orElse(0);
        String avgBedTime = averageTimeOfDay(logs.stream().map(SleepLog::getBedAt).toList());
        String avgWakeTime = averageTimeOfDay(logs.stream().map(SleepLog::getWakeAt).toList());

        Summary summary = new Summary(logs.size(), avgDurationMin, avgBedTime, avgWakeTime);
        return new SleepLogListResponse(logs.stream().map(SleepLogView::from).toList(), summary);
    }

    @Transactional(readOnly = true)
    public SleepLogView getOne(Long userId, Long sleepLogId) {
        return SleepLogView.from(findOwned(userId, sleepLogId));
    }

    public SleepLogView update(Long userId, Long sleepLogId, UpdateSleepLogRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        SleepLog log = findOwned(userId, sleepLogId);

        OffsetDateTime bedAt = request.bedAt() != null ? request.bedAt() : log.getBedAt();
        OffsetDateTime wakeAt = request.wakeAt() != null ? request.wakeAt() : log.getWakeAt();
        Integer quality = request.quality() != null ? request.quality() : log.getQuality();

        if (wakeAt != null) {
            validateClosedRange(bedAt, wakeAt);
            requireNoClosedOverlap(userId, bedAt, wakeAt, sleepLogId);
        }

        log.update(bedAt, wakeAt, quality, wakeAt == null ? null : wakeAt.toLocalDate());
        return SleepLogView.from(log);
    }

    public void delete(Long userId, Long sleepLogId) {
        sleepLogRepository.delete(findOwned(userId, sleepLogId));
    }

    private boolean isWithinGoal(OffsetDateTime actualBedAt, Plan plan) {
        OffsetDateTime target = plan.getUserBedAt() != null ? plan.getUserBedAt() : plan.getRecommendedBedAt();
        return Duration.between(target, actualBedAt).abs().compareTo(GOAL_MET_TOLERANCE) <= 0;
    }

    private void validateClosedRange(OffsetDateTime bedAt, OffsetDateTime wakeAt) {
        if (!wakeAt.isAfter(bedAt) || Duration.between(bedAt, wakeAt).compareTo(MAX_SLEEP_DURATION) > 0) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
    }

    /** 명세: "이미 종료된 기록과 겹치면" — OPEN 기록은 끝이 정의되지 않아 겹침 판정에서 제외. */
    private void requireNoClosedOverlap(Long userId, OffsetDateTime start, OffsetDateTime end, Long excludeId) {
        boolean overlaps = sleepLogRepository.findByUserIdAndStatus(userId, SleepLogStatus.CLOSED).stream()
                .filter(l -> !l.getId().equals(excludeId))
                .anyMatch(l -> l.getBedAt().isBefore(end) && start.isBefore(l.getWakeAt()));
        if (overlaps) {
            throw new ApiException(ErrorCode.SLEEP_LOG_OVERLAP);
        }
    }

    /** 정오를 기준으로 그 이전 시각은 다음날로 보고 평균 낸 뒤 모듈로 — 취침/기상 시각은 거의 항상 이 경계를 안 넘는다. */
    private String averageTimeOfDay(List<OffsetDateTime> times) {
        List<OffsetDateTime> present = times.stream().filter(t -> t != null).toList();
        if (present.isEmpty()) {
            return null;
        }
        double total = 0;
        for (OffsetDateTime t : present) {
            LocalTime lt = t.toLocalTime();
            int minuteOfDay = lt.getHour() * 60 + lt.getMinute();
            total += minuteOfDay < 12 * 60 ? minuteOfDay + 1440 : minuteOfDay;
        }
        int avgMin = (int) Math.round(total / present.size()) % 1440;
        return "%02d:%02d".formatted(avgMin / 60, avgMin % 60);
    }

    private SleepLog findOwned(Long userId, Long sleepLogId) {
        SleepLog log = sleepLogRepository.findById(sleepLogId)
                .orElseThrow(() -> new ApiException(ErrorCode.SLEEP_LOG_NOT_FOUND));
        if (!log.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return log;
    }

    private User findUserWithConsent(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
        if (!user.hasAgreedTo(ConsentType.HEALTH_DATA)) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED);
        }
        return user;
    }
}
