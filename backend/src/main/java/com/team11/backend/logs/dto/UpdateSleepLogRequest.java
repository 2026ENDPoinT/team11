package com.team11.backend.logs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;

/**
 * ponytail: 명세는 quality:null로 "평가 삭제"를 구분하지만, record는 생략/null을 구분 못해서
 * 이번엔 quality를 보내지 않는 것과 null로 보내는 것을 동일하게(변경 없음) 취급한다.
 * 평가 삭제가 꼭 필요해지면 그때 별도 처리(JsonNode 등)를 추가.
 */
public record UpdateSleepLogRequest(
        OffsetDateTime bedAt,
        OffsetDateTime wakeAt,
        @Min(1) @Max(5) Integer quality
) {

    public boolean isEmpty() {
        return bedAt == null && wakeAt == null && quality == null;
    }
}
