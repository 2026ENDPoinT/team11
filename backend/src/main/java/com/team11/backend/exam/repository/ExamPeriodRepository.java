package com.team11.backend.exam.repository;

import com.team11.backend.exam.domain.ExamPeriod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamPeriodRepository extends JpaRepository<ExamPeriod, Long> {

    List<ExamPeriod> findByUserIdOrderByFromDateDesc(Long userId);
}
