package com.team11.backend.logs.repository;

import com.team11.backend.logs.domain.SleepLog;
import com.team11.backend.logs.domain.SleepLogSource;
import com.team11.backend.logs.domain.SleepLogStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SleepLogRepository extends JpaRepository<SleepLog, Long> {

    Optional<SleepLog> findFirstByUserIdAndStatus(Long userId, SleepLogStatus status);

    Optional<SleepLog> findByUserIdAndDateAndSource(Long userId, LocalDate date, SleepLogSource source);

    List<SleepLog> findByUserIdAndStatus(Long userId, SleepLogStatus status);

    List<SleepLog> findByUserIdAndDateBetweenOrderByDateDesc(Long userId, LocalDate from, LocalDate to);
}
