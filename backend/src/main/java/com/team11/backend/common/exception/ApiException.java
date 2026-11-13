package com.team11.backend.common.exception;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.FieldViolation;
import java.util.List;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(ErrorCode errorCode, Object details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    /** 서비스 레이어에서 필드 하나의 검증 실패를 VALIDATION_FAILED로 던질 때. */
    public static ApiException invalid(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, List.of(new FieldViolation(field, reason)));
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
