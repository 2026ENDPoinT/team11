package com.team11.backend.events.dto;

/** 반복 일정은 2차라 항상 이 고정값만 내려준다(저장도 안 함). */
public record RepeatView(String freq, int interval, String until) {

    public static RepeatView none() {
        return new RepeatView("NONE", 1, null);
    }
}
