package com.team11.backend.exam.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "exam_periods")
@Getter
@NoArgsConstructor
public class ExamPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String name;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer dailyStudyHoursMax;
    private int minSleepMin;
    private boolean includeNaps;

    @OneToMany(mappedBy = "examPeriod", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamPeriodExam> exams = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public ExamPeriod(Long userId, String name, LocalDate fromDate, LocalDate toDate, Integer dailyStudyHoursMax,
                       int minSleepMin, boolean includeNaps) {
        this.userId = userId;
        this.name = name;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.dailyStudyHoursMax = dailyStudyHoursMax;
        this.minSleepMin = minSleepMin;
        this.includeNaps = includeNaps;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean overlaps(LocalDate otherFrom, LocalDate otherTo) {
        return !toDate.isBefore(otherFrom) && !fromDate.isAfter(otherTo);
    }

    public void replaceExams(List<ExamPeriodExam> newExams) {
        exams.clear();
        for (ExamPeriodExam exam : newExams) {
            exam.assignTo(this);
            exams.add(exam);
        }
    }

    public void update(String name, LocalDate fromDate, LocalDate toDate, Integer dailyStudyHoursMax,
                        int minSleepMin, boolean includeNaps) {
        this.name = name;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.dailyStudyHoursMax = dailyStudyHoursMax;
        this.minSleepMin = minSleepMin;
        this.includeNaps = includeNaps;
    }
}
