package com.team11.backend.common;

public record ApiError(String code, String message, Object details) {

    public static ApiError of(ErrorCode code) {
        return new ApiError(code.name(), code.getMessage(), null);
    }

    public static ApiError of(ErrorCode code, Object details) {
        return new ApiError(code.name(), code.getMessage(), details);
    }
}
