package com.team11.backend.ai;

/** AI 서버가 422(도메인 오류: 대상 시험 없음·실행 가능한 계획 없음·지원 범위 초과)로 거절한 경우. 호출부가 모드별로 해석한다. */
public class AiRejectedException extends RuntimeException {

    private final String code;

    public AiRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
