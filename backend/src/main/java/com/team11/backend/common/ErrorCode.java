package com.team11.backend.common;

import org.springframework.http.HttpStatus;

/**
 * Notion "API DOCS SUBPAGE" 2.5 에러 코드표. 도메인이 늘어날 때마다 해당 도메인에서 쓰는 코드만 추가한다.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않아요."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었어요."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "토큰이 올바르지 않아요."),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요."),
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "필수 동의가 필요해요."),
    USER_DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디예요."),
    USER_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않아요."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했어요.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
