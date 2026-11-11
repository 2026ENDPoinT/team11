package com.team11.backend.logs.domain;

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

@Entity
@Table(name = "prep_logs")
@Getter
@NoArgsConstructor
public class PrepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private LocalDate date;
    private OffsetDateTime wakeAt;
    private OffsetDateTime departureAt;
    private int prepMin;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public PrepLog(Long userId, LocalDate date, OffsetDateTime wakeAt, OffsetDateTime departureAt) {
        this.userId = userId;
        this.date = date;
        update(wakeAt, departureAt);
    }

    public void update(OffsetDateTime wakeAt, OffsetDateTime departureAt) {
        this.wakeAt = wakeAt;
        this.departureAt = departureAt;
        this.prepMin = (int) Duration.between(wakeAt, departureAt).toMinutes();
    }
}
