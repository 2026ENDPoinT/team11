package com.team11.backend.events.domain;

/** 3-3 일정 추가 시트의 4종. AI-03 계약에는 ETC가 없어 전송 시 OTHER로 바꾼다. */
public enum EventType {
    ASSIGNMENT, EXAM, PERSONAL, ETC;

    public String aiName() {
        return this == ETC ? "OTHER" : name();
    }
}
