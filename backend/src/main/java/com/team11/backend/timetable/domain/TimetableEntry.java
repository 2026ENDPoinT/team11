package com.team11.backend.timetable.domain;

import com.team11.backend.common.domain.Source;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "timetable_entries")
@Getter
@NoArgsConstructor
public class TimetableEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String semester;

    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    private LocalTime startTime;
    private LocalTime endTime;
    private String courseName;
    private String location;

    @Enumerated(EnumType.STRING)
    private Source source;

    private String externalId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public TimetableEntry(Long userId, String semester, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                           String courseName, String location, Source source, String externalId) {
        this.userId = userId;
        this.semester = semester;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.courseName = courseName;
        this.location = location;
        this.source = source;
        this.externalId = externalId;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void updateLocation(String location) {
        this.location = location;
    }

    public void updateAll(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String courseName,
                           String location) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.courseName = courseName;
        this.location = location;
    }
}
