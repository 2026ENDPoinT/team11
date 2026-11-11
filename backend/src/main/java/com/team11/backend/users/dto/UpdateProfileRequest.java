package com.team11.backend.users.dto;

import com.team11.backend.users.domain.Chronotype;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 12, message = "2~12자여야 해요.")
        String nickname,

        Chronotype chronotype,

        @Min(value = 300, message = "300~600 범위여야 해요.") @Max(value = 600, message = "300~600 범위여야 해요.")
        Integer sleepNeedMin,

        @Min(value = 0, message = "0~60 범위여야 해요.") @Max(value = 60, message = "0~60 범위여야 해요.")
        Integer sleepLatencyMin,

        @Min(value = 0, message = "0~240 범위여야 해요.") @Max(value = 240, message = "0~240 범위여야 해요.")
        Integer commuteMin,

        @Min(value = 0, message = "0~240 범위여야 해요.") @Max(value = 240, message = "0~240 범위여야 해요.")
        Integer prepMin,

        @Min(value = 0, message = "0~60 범위여야 해요.") @Max(value = 60, message = "0~60 범위여야 해요.")
        Integer bufferMin,

        @Min(value = 240, message = "240~480 범위여야 해요.") @Max(value = 480, message = "240~480 범위여야 해요.")
        Integer minSleepMin
) {

    public boolean isEmpty() {
        return nickname == null && chronotype == null && sleepNeedMin == null && sleepLatencyMin == null
                && commuteMin == null && prepMin == null && bufferMin == null && minSleepMin == null;
    }
}
