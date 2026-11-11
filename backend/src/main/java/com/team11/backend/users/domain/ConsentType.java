package com.team11.backend.users.domain;

public enum ConsentType {
    TERMS(true), PRIVACY(true), HEALTH_DATA(false), MARKETING(false);

    private final boolean required;

    ConsentType(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }
}
