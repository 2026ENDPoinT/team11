package com.team11.backend.users.domain;

/** 온보딩 0-3 동의 화면 순서대로. 필수 3개는 철회 불가. */
public enum ConsentType {
    MEDICAL_DISCLAIMER(true), TERMS(true), PRIVACY(true), NOTIFICATION(false);

    private final boolean required;

    ConsentType(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }
}
