package com.team11.backend.plans.repository;

import com.team11.backend.plans.domain.Plan;
import com.team11.backend.plans.domain.PlanMode;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByUserIdAndDateAndMode(Long userId, LocalDate date, PlanMode mode);
}
