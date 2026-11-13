package com.team11.backend.plans.service;

import com.team11.backend.common.Kst;
import com.team11.backend.plans.domain.PlanDraft;
import com.team11.backend.plans.domain.PlanStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** AI-02 실패 시 규칙 폴백. 기상 = 첫 일정 − (통학+준비+여유), 취침 = 기상 − (목표수면+입면). 첫 일정이 없으면 09:00 기상. */
public final class RulePlanner {

    public static final LocalTime FREE_DAY_WAKE_TIME = LocalTime.of(9, 0);

    private RulePlanner() {
    }

    public record Profile(int sleepNeedMin, int sleepLatencyMin, int commuteMin, int prepMin, int bufferMin) {
    }

    /** 기상일의 가장 이른 수업/일정. 홈 카드 basisText("09:00 오픈소스소프트웨어")의 출처. */
    public record FirstEvent(String title, OffsetDateTime startAt) {

        public String basisText() {
            return "%s %s".formatted(Kst.timeOf(startAt).toString(), title);
        }
    }

    public static OffsetDateTime wakeDeadline(LocalDate targetDate, FirstEvent firstEvent, Profile profile) {
        return firstEvent != null
                ? firstEvent.startAt().minusMinutes(profile.commuteMin() + profile.prepMin() + profile.bufferMin())
                : Kst.at(targetDate, FREE_DAY_WAKE_TIME);
    }

    public static PlanDraft plan(OffsetDateTime now, LocalDate targetDate, FirstEvent firstEvent, Profile profile) {
        OffsetDateTime wakeAt = wakeDeadline(targetDate, firstEvent, profile);
        OffsetDateTime bedAt = wakeAt.minusMinutes(profile.sleepNeedMin() + profile.sleepLatencyMin());

        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (firstEvent != null) {
            reasons.add("첫 일정(%s)에 맞춰 준비·통학 시간을 뺐어요.".formatted(firstEvent.basisText()));
        } else {
            reasons.add("일정이 없어서 평소 리듬(09:00 기상)에 맞췄어요.");
        }
        reasons.add("목표 수면 %d시간 %d분 기준이에요.".formatted(profile.sleepNeedMin() / 60, profile.sleepNeedMin() % 60));
        if (bedAt.isBefore(now)) {
            warnings.add("지금 바로 잠들어도 목표 수면시간을 다 채우기 어려워요.");
        }
        return new PlanDraft(PlanStatus.READY, bedAt, wakeAt, profile.sleepNeedMin(),
                firstEvent != null ? firstEvent.basisText() : null, PlanDraft.RULE, null, reasons, warnings);
    }
}
