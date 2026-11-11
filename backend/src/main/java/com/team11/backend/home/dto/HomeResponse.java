package com.team11.backend.home.dto;

import com.team11.backend.plans.domain.FirstEventKind;
import com.team11.backend.plans.domain.PlanMode;
import com.team11.backend.users.dto.CharacterView;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record HomeResponse(
        OffsetDateTime now, TonightView tonight, List<NextEventView> nextEvents, HomeStreakView streak,
        CharacterView character, OpenSleepLogView openSleepLog, Integer sleepDebtMin, int unreadNotifications,
        List<BannerView> banners, TipView tip
) {

    public record TonightView(Long planId, LocalDate targetDate, PlanMode mode, OffsetDateTime recommendedBedAt,
                               OffsetDateTime userBedAt, OffsetDateTime recommendedWakeAt, long minutesUntilBed,
                               List<String> reasons, List<String> warnings) {
    }

    public record NextEventView(FirstEventKind kind, String title, OffsetDateTime startAt) {
    }

    public record HomeStreakView(int currentCount, int bestCount, boolean atRisk) {
    }

    public record OpenSleepLogView(Long sleepLogId, OffsetDateTime bedAt) {
    }

    public record BannerView(String type, String message, Long relatedEventId) {
    }

    public record TipView(Long tipId, String text) {
    }
}
