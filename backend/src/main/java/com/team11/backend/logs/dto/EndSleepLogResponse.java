package com.team11.backend.logs.dto;

/** streak/sleepDebtMin은 Streak/Home 도메인이 없어서 항상 null(지어내지 않음). */
public record EndSleepLogResponse(SleepLogView sleepLog, boolean goalMet, Object streak, Integer sleepDebtMin) {

    public static EndSleepLogResponse of(SleepLogView sleepLog, boolean goalMet) {
        return new EndSleepLogResponse(sleepLog, goalMet, null, null);
    }
}
