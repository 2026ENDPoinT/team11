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

/** 기상일(date) × 모드당 1건. 재생성(POST)은 같은 행을 덮어쓴다. */
@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private PlanMode mode;

    @Enumerated(EnumType.STRING)
    private PlanStatus status;

    private OffsetDateTime bedAt;
    private OffsetDateTime wakeAt;
    private Integer sleepMin;
    private String basisText;
    private String generatedBy;
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> reasons;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> warnings;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Plan(Long userId, LocalDate date, PlanMode mode, PlanDraft draft) {
        this.userId = userId;
        this.date = date;
        this.mode = mode;
        replace(draft);
    }

    public void replace(PlanDraft d) {
        this.status = d.status();
        this.bedAt = d.bedAt();
        this.wakeAt = d.wakeAt();
        this.sleepMin = d.sleepMin();
        this.basisText = d.basisText();
        this.generatedBy = d.generatedBy();
        this.modelVersion = d.modelVersion();
        this.reasons = d.reasons() == null ? List.of() : d.reasons();
        this.warnings = d.warnings() == null ? List.of() : d.warnings();
    }
}
