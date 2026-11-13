package com.team11.backend.logs.domain;

import com.team11.backend.common.Kst;
import jakarta.persistence.Entity;
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

/** 수동 입력 전용 수면 기록. date = KST 기상일, 하루 1건. durationMin은 취침~기상 간격(침대 체류). */
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
    private int durationMin;
    private Integer quality;
    private String memo;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public SleepLog(Long userId, OffsetDateTime bedAt, OffsetDateTime wakeAt, Integer quality, String memo) {
        this.userId = userId;
        update(bedAt, wakeAt, quality, memo);
    }

    public static LocalDate dateFor(OffsetDateTime wakeAt) {
        return Kst.dateOf(wakeAt);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void update(OffsetDateTime bedAt, OffsetDateTime wakeAt, Integer quality, String memo) {
        this.bedAt = bedAt;
        this.wakeAt = wakeAt;
        this.date = dateFor(wakeAt);
        this.durationMin = (int) Duration.between(bedAt, wakeAt).toMinutes();
        this.quality = quality;
        this.memo = memo;
    }
}
