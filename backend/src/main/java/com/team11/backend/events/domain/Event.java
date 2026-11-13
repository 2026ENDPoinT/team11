package com.team11.backend.events.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String title;

    @Enumerated(EnumType.STRING)
    private EventType type;

    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private boolean allDay;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Event(Long userId, String title, EventType type, OffsetDateTime startAt, OffsetDateTime endAt, boolean allDay) {
        this.userId = userId;
        update(title, type, startAt, endAt, allDay);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void update(String title, EventType type, OffsetDateTime startAt, OffsetDateTime endAt, boolean allDay) {
        this.title = title;
        this.type = type;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
    }

    /** 끝 시각이 없으면 시작 시각을 끝으로 본다. */
    public OffsetDateTime effectiveEndAt() {
        return endAt != null ? endAt : startAt;
    }
}
