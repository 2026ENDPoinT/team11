package com.team11.backend.users.dto;

import com.team11.backend.users.domain.Chronotype;
import com.team11.backend.users.domain.User;
import java.time.Instant;

public record UserProfileResponse(
        Long userId, String loginId, String nickname, boolean onboardingDone,
        Settings settings, CharacterView character, Integer prepMinEstimate, Instant createdAt
) {

    public record Settings(Chronotype chronotype, int sleepNeedMin, int sleepLatencyMin, int commuteMin,
                            int prepMin, int bufferMin, int minSleepMin) {

        public static Settings from(User user) {
            return new Settings(user.getChronotype(), user.getSleepNeedMin(), user.getSleepLatencyMin(),
                    user.getCommuteMin(), user.getPrepMin(), user.getBufferMin(), user.getMinSleepMin());
        }
    }

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(), user.getLoginId(), user.getNickname(), user.isOnboardingDone(),
                Settings.from(user), CharacterView.from(user.getCharacter()),
                null, // prepMinEstimate: Logs 도메인 생기기 전까지 항상 null
                user.getCreatedAt());
    }
}
