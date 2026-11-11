package com.team11.backend.logs.repository;

import com.team11.backend.logs.domain.PrepLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrepLogRepository extends JpaRepository<PrepLog, Long> {

    Optional<PrepLog> findByUserIdAndDate(Long userId, LocalDate date);

    List<PrepLog> findByUserIdAndDateBetweenOrderByDateDesc(Long userId, LocalDate from, LocalDate to);

    List<PrepLog> findTop20ByUserIdOrderByDateDesc(Long userId);
}
