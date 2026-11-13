package com.team11.backend.tips.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TipServiceTest {

    private final TipService tipService = new TipService();

    @Test
    void examSoon_returnsExamSituationTip() {
        TipService.Tip tip = tipService.getTodayTip(1L, true, true);
        assertThat(tip.id()).isIn(1L, 2L);
    }

    @Test
    void lateNightWithoutExam_returnsLateNightTip() {
        TipService.Tip tip = tipService.getTodayTip(1L, false, true);
        assertThat(tip.id()).isIn(3L, 4L);
    }

    @Test
    void noSituation_returnsGeneralTip() {
        TipService.Tip tip = tipService.getTodayTip(1L, false, false);
        assertThat(tip.id()).isIn(5L, 6L, 7L);
    }

    @Test
    void sameUserSameSituation_returnsSameTipEveryCall() {
        TipService.Tip first = tipService.getTodayTip(42L, false, false);
        TipService.Tip second = tipService.getTodayTip(42L, false, false);
        assertThat(first.id()).isEqualTo(second.id());
    }
}
