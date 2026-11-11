package com.team11.backend.logs.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.FieldViolation;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.logs.domain.PrepLog;
import com.team11.backend.logs.dto.CreatePrepLogRequest;
import com.team11.backend.logs.dto.CreatePrepLogResponse;
import com.team11.backend.logs.dto.PrepLogListResponse;
import com.team11.backend.logs.dto.PrepLogView;
import com.team11.backend.logs.repository.PrepLogRepository;
import com.team11.backend.users.domain.ConsentType;
import com.team11.backend.users.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PrepLogService {

    private static final int MIN_PREP_MIN = 1;
    private static final int MAX_PREP_MIN = 360;
    private static final long MAX_RANGE_DAYS = 93;
    private static final double ESTIMATE_PERCENTILE = 0.8;

    private final PrepLogRepository prepLogRepository;
    private final UserRepository userRepository;

    public PrepLogService(PrepLogRepository prepLogRepository, UserRepository userRepository) {
        this.prepLogRepository = prepLogRepository;
        this.userRepository = userRepository;
    }

    public record UpsertResult(CreatePrepLogResponse response, boolean created) {
    }

    public UpsertResult createOrReplace(Long userId, CreatePrepLogRequest request) {
        requireHealthDataConsent(userId);
        if (!request.departureAt().isAfter(request.wakeAt())) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
        int prepMin = (int) Duration.between(request.wakeAt(), request.departureAt()).toMinutes();
        if (prepMin < MIN_PREP_MIN || prepMin > MAX_PREP_MIN) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("prepMin", "0분 초과 360분 이하여야 해요.")));
        }

        var existing = prepLogRepository.findByUserIdAndDate(userId, request.date());
        boolean created = existing.isEmpty();
        PrepLog log = existing.orElseGet(() -> new PrepLog(userId, request.date(), request.wakeAt(), request.departureAt()));
        log.update(request.wakeAt(), request.departureAt());
        prepLogRepository.save(log);

        Integer estimate = computeEstimate(userId);
        return new UpsertResult(new CreatePrepLogResponse(PrepLogView.from(log), estimate), created);
    }

    @Transactional(readOnly = true)
    public PrepLogListResponse list(Long userId, LocalDate from, LocalDate to) {
        requireHealthDataConsent(userId);
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.DATE_RANGE_TOO_LARGE);
        }
        List<PrepLog> logs = prepLogRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, from, to);
        Integer avgPrepMin = logs.isEmpty() ? null
                : (int) logs.stream().mapToInt(PrepLog::getPrepMin).average().orElse(0);
        return new PrepLogListResponse(logs.stream().map(PrepLogView::from).toList(), avgPrepMin, computeEstimate(userId));
    }

    /** AI prep-time/estimate는 이제 서버에 없어서(CLAUDE.md §6) Spring 규칙만: 최근 기록 prepMin의 80% 분위수. */
    private Integer computeEstimate(Long userId) {
        List<Integer> values = prepLogRepository.findTop20ByUserIdOrderByDateDesc(userId).stream()
                .map(PrepLog::getPrepMin)
                .sorted()
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        int rank = (int) Math.ceil(ESTIMATE_PERCENTILE * values.size());
        return values.get(Math.max(0, rank - 1));
    }

    private void requireHealthDataConsent(Long userId) {
        var user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
        if (!user.hasAgreedTo(ConsentType.HEALTH_DATA)) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED);
        }
    }
}
