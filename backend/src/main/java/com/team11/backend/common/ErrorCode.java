package com.team11.backend.common;

import org.springframework.http.HttpStatus;

/** 실제로 던지는 코드만 둔다. 새 코드를 추가하면 Notion 에러 코드표에도 반영할 것. */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않아요."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었어요."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "토큰이 올바르지 않아요."),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),
    USER_DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디예요."),
    USER_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않아요."),
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "필수 동의가 필요해요."),
    CONSENT_NOT_REVOCABLE(HttpStatus.BAD_REQUEST, "철회할 수 없는 필수 동의예요."),
    SEMESTER_NOT_FOUND(HttpStatus.NOT_FOUND, "지원하지 않는 학기예요."),
    TIMETABLE_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "수업 항목이 없어요."),
    TIMETABLE_OVERLAP(HttpStatus.CONFLICT, "같은 시간에 이미 등록된 수업이 있어요."),
    TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "시작/종료 시각이 올바르지 않아요."),
    DATE_RANGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "조회 기간이 너무 길어요."),
    EVERYTIME_URL_INVALID(HttpStatus.BAD_REQUEST, "에브리타임 시간표 공유 링크 형식이 올바르지 않아요."),
    EVERYTIME_IMPORT_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "에브리타임 시간표를 가져오지 못했어요. 공개 시간표인지 확인해 주세요."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "일정이 없어요."),
    SLEEP_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "수면 기록이 없어요."),
    SLEEP_LOG_DUPLICATE_DATE(HttpStatus.CONFLICT, "그 날짜에는 이미 수면 기록이 있어요."),
    SLEEP_HISTORY_INSUFFICIENT(HttpStatus.UNPROCESSABLE_CONTENT, "집중 모드 추천에는 최근 수면 기록이 3일 이상 필요해요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 플랜을 만들 수 없어요. 잠시 후 다시 시도해 주세요."),
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
