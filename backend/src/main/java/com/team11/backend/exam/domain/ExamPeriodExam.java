package com.team11.backend.exam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exam_period_exams")
@Getter
@NoArgsConstructor
public class ExamPeriodExam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exam_period_id")
    private ExamPeriod examPeriod;

    private Long eventId;
    private int difficulty;
    private int studyHoursNeeded;

    public ExamPeriodExam(Long eventId, int difficulty, int studyHoursNeeded) {
        this.eventId = eventId;
        this.difficulty = difficulty;
        this.studyHoursNeeded = studyHoursNeeded;
    }

    void assignTo(ExamPeriod examPeriod) {
        this.examPeriod = examPeriod;
    }
}
