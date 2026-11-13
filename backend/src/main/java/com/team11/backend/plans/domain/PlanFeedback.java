package com.team11.backend.plans.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** PLAN-06. 플랜 하나당 한 건만 존재(plan_id가 PK) — 다시 보내면 덮어씀. */
@Entity
@Table(name = "plan_feedbacks")
@Getter
@NoArgsConstructor
public class PlanFeedback {

    @Id
    private Long planId;

    @Enumerated(EnumType.STRING)
    private PlanFeedbackRating rating;

    private Boolean followed;

    @Enumerated(EnumType.STRING)
    private PlanFeedbackReason reason;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public PlanFeedback(Long planId, PlanFeedbackRating rating, Boolean followed, PlanFeedbackReason reason) {
        this.planId = planId;
        this.rating = rating;
        this.followed = followed;
        this.reason = reason;
    }

    public void update(PlanFeedbackRating rating, Boolean followed, PlanFeedbackReason reason) {
        this.rating = rating;
        this.followed = followed;
        this.reason = reason;
    }
}
