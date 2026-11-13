package com.team11.backend.logs.repository;

import com.team11.backend.logs.domain.SleepStage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SleepStageRepository extends JpaRepository<SleepStage, Long> {

    List<SleepStage> findBySleepLogIdOrderByStartAtAsc(Long sleepLogId);

    List<SleepStage> findBySleepLogIdInOrderByStartAtAsc(List<Long> sleepLogIds);

    void deleteBySleepLogId(Long sleepLogId);
}
