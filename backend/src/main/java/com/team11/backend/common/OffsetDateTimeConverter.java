package com.team11.backend.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * pgjdbc의 getObject(..., OffsetDateTime.class)는 DB 세션 TimeZone과 무관하게 항상 UTC(Z) 오프셋으로 값을
 * 돌려준다(같은 instant지만 표시가 Z로 바뀜 — hibernate.jdbc.time_zone으로도 못 고친다, 실측 확인됨).
 * 모든 OffsetDateTime 엔티티 필드에 자동 적용(autoApply)해서 Hibernate가 읽는 즉시 +09:00로 다시 표현한다 —
 * 변환 규칙을 이 한 곳에서만 다룬다(CLAUDE.md §4 참고).
 */
@Converter(autoApply = true)
public class OffsetDateTimeConverter implements AttributeConverter<OffsetDateTime, OffsetDateTime> {

    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    @Override
    public OffsetDateTime convertToDatabaseColumn(OffsetDateTime attribute) {
        return attribute;
    }

    @Override
    public OffsetDateTime convertToEntityAttribute(OffsetDateTime dbData) {
        return dbData == null ? null : dbData.withOffsetSameInstant(KST);
    }
}
