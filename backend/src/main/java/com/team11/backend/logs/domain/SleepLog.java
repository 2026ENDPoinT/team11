package com.team11.backend.logs.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "sleep_logs")
@Getter
@NoArgsConstructor
public class SleepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private LocalDate date;
    private OffsetDateTime bedAt;
    private OffsetDateTime wakeAt;
    private Integer durationMin;
    private Integer quality;

    @Enumerated(EnumType.STRING)
    private SleepLogStatus status;

    @Enumerated(EnumType.STRING)
    private SleepLogSource source;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /** LOG-01: 열린 기록으로 시작. */
    public SleepLog(Long userId, OffsetDateTime bedAt, SleepLogSource source) {
        this.userId = userId;
        this.bedAt = bedAt;
        this.source = source;
        this.status = SleepLogStatus.OPEN;
    }

    /** LOG-04: 처음부터 닫힌 기록으로 직접 입력. */
    public SleepLog(Long userId, OffsetDateTime bedAt, OffsetDateTime wakeAt, Integer quality, LocalDate date,
                     SleepLogSource source) {
        this.userId = userId;
        this.bedAt = bedAt;
        this.source = source;
        close(wakeAt, quality, date);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void close(OffsetDateTime wakeAt, Integer quality, LocalDate date) {
        this.wakeAt = wakeAt;
        this.quality = quality;
        this.date = date;
        this.durationMin = (int) Duration.between(bedAt, wakeAt).toMinutes();
        this.status = SleepLogStatus.CLOSED;
    }

    public void update(OffsetDateTime bedAt, OffsetDateTime wakeAt, Integer quality, LocalDate date) {
        this.bedAt = bedAt;
        this.wakeAt = wakeAt;
        this.quality = quality;
        this.date = date;
        this.durationMin = wakeAt == null ? null : (int) Duration.between(bedAt, wakeAt).toMinutes();
        this.status = wakeAt == null ? SleepLogStatus.OPEN : SleepLogStatus.CLOSED;
    }
}
