package com.team11.backend.users.dto;

import com.team11.backend.users.domain.User;

public record UserProfileResponse(Long userId, String loginId, String name, boolean onboardingDone,
                                   SleepSettingsView settings) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getLoginId(), user.getName(), user.isOnboardingDone(),
                SleepSettingsView.from(user));
    }
}
