package com.team11.backend.events.domain;

import com.team11.backend.common.domain.Source;
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

    @Enumerated(EnumType.STRING)
    private EventType type;

    private String title;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private boolean allDay;

    @Enumerated(EnumType.STRING)
    private EventTag tag;

    private String note;

    @Enumerated(EnumType.STRING)
    private Source source;

    private String externalId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Event(Long userId, EventType type, String title, OffsetDateTime startAt, OffsetDateTime endAt,
                 boolean allDay, EventTag tag, String note, Source source, String externalId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.tag = tag;
        this.note = note;
        this.source = source;
        this.externalId = externalId;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void updateTagAndNote(EventTag tag, String note) {
        this.tag = tag;
        this.note = note;
    }

    public void updateAll(EventType type, String title, OffsetDateTime startAt, OffsetDateTime endAt, boolean allDay,
                           EventTag tag, String note) {
        this.type = type;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.tag = tag;
        this.note = note;
    }
}
