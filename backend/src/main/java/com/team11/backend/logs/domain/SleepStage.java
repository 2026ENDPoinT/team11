package com.team11.backend.logs.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 웨어러블(애플워치/갤럭시워치)이 기록한 실제 수면 단계 구간. SYNC-01 배치 동기화에서만 생성된다. */
@Entity
@Table(name = "sleep_stages")
@Getter
@NoArgsConstructor
public class SleepStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sleepLogId;

    @Enumerated(EnumType.STRING)
    private SleepStageType stage;

    private OffsetDateTime startAt;
    private OffsetDateTime endAt;

    @CreationTimestamp
    private Instant createdAt;

    public SleepStage(Long sleepLogId, SleepStageType stage, OffsetDateTime startAt, OffsetDateTime endAt) {
        this.sleepLogId = sleepLogId;
        this.stage = stage;
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
