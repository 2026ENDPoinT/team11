package com.team11.backend.plans.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    private PlanMode mode;

    @Enumerated(EnumType.STRING)
    private PlanStatus status;

    private OffsetDateTime recommendedBedAt;
    private OffsetDateTime recommendedWakeAt;
    private OffsetDateTime userBedAt;
    private int sleepMin;

    private Integer breakdownCommuteMin;
    private Integer breakdownPrepMin;
    private Integer breakdownBufferMin;
    private Integer breakdownSleepNeedMin;
    private Integer breakdownSleepLatencyMin;

    @Enumerated(EnumType.STRING)
    private FirstEventKind firstEventKind;
    private String firstEventTitle;
    private OffsetDateTime firstEventStartAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> reasons;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> warnings;

    private String generatedBy;
    private String modelVersion;
    private OffsetDateTime generatedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Plan(Long userId, LocalDate targetDate, PlanMode mode, OffsetDateTime recommendedBedAt,
                OffsetDateTime recommendedWakeAt, int sleepMin, Integer breakdownCommuteMin, Integer breakdownPrepMin,
                Integer breakdownBufferMin, Integer breakdownSleepNeedMin, Integer breakdownSleepLatencyMin,
                FirstEventKind firstEventKind, String firstEventTitle, OffsetDateTime firstEventStartAt,
                List<String> reasons, List<String> warnings, String generatedBy, String modelVersion,
                OffsetDateTime generatedAt) {
        this.userId = userId;
        this.targetDate = targetDate;
        this.mode = mode;
        this.status = PlanStatus.ACTIVE;
        this.recommendedBedAt = recommendedBedAt;
        this.recommendedWakeAt = recommendedWakeAt;
        this.sleepMin = sleepMin;
        this.breakdownCommuteMin = breakdownCommuteMin;
        this.breakdownPrepMin = breakdownPrepMin;
        this.breakdownBufferMin = breakdownBufferMin;
        this.breakdownSleepNeedMin = breakdownSleepNeedMin;
        this.breakdownSleepLatencyMin = breakdownSleepLatencyMin;
        this.firstEventKind = firstEventKind;
        this.firstEventTitle = firstEventTitle;
        this.firstEventStartAt = firstEventStartAt;
        this.reasons = reasons;
        this.warnings = warnings;
        this.generatedBy = generatedBy;
        this.modelVersion = modelVersion;
        this.generatedAt = generatedAt;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }
}
