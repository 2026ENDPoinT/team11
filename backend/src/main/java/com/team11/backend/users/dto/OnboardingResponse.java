package com.team11.backend.users.dto;

import com.team11.backend.users.domain.Chronotype;

public record OnboardingResponse(
        Chronotype chronotype, String midSleepFreeday, SuggestedSettings suggestedSettings, boolean onboardingDone
) {

    public record SuggestedSettings(int sleepNeedMin, int sleepLatencyMin, int commuteMin, int prepMin, int bufferMin) {
    }
}
