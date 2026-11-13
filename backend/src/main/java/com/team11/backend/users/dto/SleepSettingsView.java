package com.team11.backend.users.dto;

import com.team11.backend.users.domain.User;

public record SleepSettingsView(int sleepNeedMin, int sleepLatencyMin, int commuteMin, int prepMin, int bufferMin,
                                 int minSleepMin, int examPrepDays, int focusMinInBedMin, int focusMaxInBedMin) {

    public static SleepSettingsView from(User u) {
        return new SleepSettingsView(u.getSleepNeedMin(), u.getSleepLatencyMin(), u.getCommuteMin(), u.getPrepMin(),
                u.getBufferMin(), u.getMinSleepMin(), u.getExamPrepDays(), u.getFocusMinInBedMin(),
                u.getFocusMaxInBedMin());
    }
}
