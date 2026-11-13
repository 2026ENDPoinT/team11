package com.team11.backend.plans.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.team11.backend.plans.domain.PlanDraft;
import com.team11.backend.plans.domain.PlanStatus;
import com.team11.backend.plans.service.RulePlanner.FirstEvent;
import com.team11.backend.plans.service.RulePlanner.Profile;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RulePlannerTest {

    private static final Profile PROFILE = new Profile(465, 15, 40, 50, 10);
    private static final LocalDate TARGET = LocalDate.of(2026, 11, 12);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-11T21:00:00+09:00");

    @Test
    void withFirstEvent_subtractsCommutePrepBufferThenSleep() {
        FirstEvent first = new FirstEvent("오픈소스소프트웨어", OffsetDateTime.parse("2026-11-12T09:00:00+09:00"));

        PlanDraft draft = RulePlanner.plan(NOW, TARGET, first, PROFILE);

        assertThat(draft.status()).isEqualTo(PlanStatus.READY);
        assertThat(draft.wakeAt()).isEqualTo(OffsetDateTime.parse("2026-11-12T07:20:00+09:00")); // 09:00 − 100분
        assertThat(draft.bedAt()).isEqualTo(OffsetDateTime.parse("2026-11-11T23:20:00+09:00")); // 07:20 − 480분
        assertThat(draft.sleepMin()).isEqualTo(465);
        assertThat(draft.basisText()).isEqualTo("09:00 오픈소스소프트웨어");
        assertThat(draft.generatedBy()).isEqualTo(PlanDraft.RULE);
        assertThat(draft.warnings()).isEmpty();
    }

    @Test
    void withoutFirstEvent_usesNineAmAndNoBasisText() {
        PlanDraft draft = RulePlanner.plan(NOW, TARGET, null, PROFILE);

        assertThat(draft.wakeAt()).isEqualTo(OffsetDateTime.parse("2026-11-12T09:00:00+09:00"));
        assertThat(draft.bedAt()).isEqualTo(OffsetDateTime.parse("2026-11-12T01:00:00+09:00"));
        assertThat(draft.basisText()).isNull();
    }

    @Test
    void bedtimeAlreadyPassed_addsWarning() {
        FirstEvent first = new FirstEvent("1교시", OffsetDateTime.parse("2026-11-12T06:00:00+09:00"));
        OffsetDateTime late = OffsetDateTime.parse("2026-11-11T23:30:00+09:00");

        PlanDraft draft = RulePlanner.plan(late, TARGET, first, PROFILE);

        assertThat(draft.bedAt()).isBefore(late);
        assertThat(draft.warnings()).hasSize(1);
    }
}
