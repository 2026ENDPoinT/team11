package com.team11.backend.plans.repository;

import com.team11.backend.plans.domain.PlanFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanFeedbackRepository extends JpaRepository<PlanFeedback, Long> {
}
