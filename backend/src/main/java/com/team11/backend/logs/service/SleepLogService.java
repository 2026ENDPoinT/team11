package com.team11.backend.logs.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.logs.domain.SleepLog;
import com.team11.backend.logs.dto.SleepLogListResponse;
import com.team11.backend.logs.dto.SleepLogRequest;
import com.team11.backend.logs.dto.SleepLogView;
import com.team11.backend.logs.repository.SleepLogRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SleepLogService {

    private static final Duration MAX_SLEEP_DURATION = Duration.ofHours(24);
    private static final long MAX_RANGE_DAYS = 93;

    private final SleepLogRepository repository;

    public SleepLogService(SleepLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SleepLogListResponse list(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.DATE_RANGE_TOO_LARGE);
        }
        return new SleepLogListResponse(between(userId, from, to).stream()
                .sorted(Comparator.comparing(SleepLog::getDate).reversed())
                .map(SleepLogView::from).toList());
    }

    /** 다른 도메인(리포트·플랜)용: 기상일 [from, to] 기록, 날짜 오름차순. */
    @Transactional(readOnly = true)
    public List<SleepLog> between(Long userId, LocalDate from, LocalDate to) {
        return repository.findByUserIdAndDateBetweenOrderByDateAsc(userId, from, to);
    }

    @Transactional(readOnly = true)
    public SleepLogView get(Long userId, Long sleepLogId) {
        return SleepLogView.from(findOwned(userId, sleepLogId));
    }

    public SleepLogView create(Long userId, SleepLogRequest request) {
        validate(request);
        requireDateFree(userId, SleepLog.dateFor(request.wakeAt()), null);
        SleepLog log = new SleepLog(userId, request.bedAt(), request.wakeAt(), request.quality(), trim(request.memo()));
        return SleepLogView.from(repository.save(log));
    }

    public SleepLogView update(Long userId, Long sleepLogId, SleepLogRequest request) {
        validate(request);
        SleepLog log = findOwned(userId, sleepLogId);
        requireDateFree(userId, SleepLog.dateFor(request.wakeAt()), sleepLogId);
        log.update(request.bedAt(), request.wakeAt(), request.quality(), trim(request.memo()));
        return SleepLogView.from(log);
    }

    public void delete(Long userId, Long sleepLogId) {
        repository.delete(findOwned(userId, sleepLogId));
    }

    private static void validate(SleepLogRequest r) {
        if (!r.wakeAt().isAfter(r.bedAt()) || Duration.between(r.bedAt(), r.wakeAt()).compareTo(MAX_SLEEP_DURATION) > 0) {
            throw new ApiException(ErrorCode.TIME_RANGE_INVALID);
        }
    }

    /** 기상일당 1건. 같은 날 기록이 있으면 그 id를 알려줘 프론트가 수정 화면으로 보낼 수 있게. */
    private void requireDateFree(Long userId, LocalDate date, Long excludeId) {
        repository.findByUserIdAndDate(userId, date)
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.SLEEP_LOG_DUPLICATE_DATE,
                            Map.of("sleepLogId", existing.getId(), "date", date.toString()));
                });
    }

    private SleepLog findOwned(Long userId, Long sleepLogId) {
        SleepLog log = repository.findById(sleepLogId).orElseThrow(() -> new ApiException(ErrorCode.SLEEP_LOG_NOT_FOUND));
        if (!log.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
        return log;
    }

    private static String trim(String memo) {
        if (memo == null) {
            return null;
        }
        String t = memo.trim();
        return t.isEmpty() ? null : t;
    }
}
