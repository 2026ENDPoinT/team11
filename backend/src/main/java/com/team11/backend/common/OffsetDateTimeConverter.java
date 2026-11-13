package com.team11.backend.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.OffsetDateTime;

/**
 * pgjdbc의 getObject(OffsetDateTime.class)는 DB 세션 TimeZone·hibernate.jdbc.time_zone과 무관하게 항상 UTC(Z)로
 * 값을 돌려준다(같은 instant지만 표시가 깨짐 — 실측 확인). 모든 엔티티의 OffsetDateTime을 읽는 즉시 +09:00로 다시
 * 표현한다. 오프셋 변환 규칙은 이 클래스와 application.yaml의 spring.jackson.time-zone 두 곳에만 있다.
 */
@Converter(autoApply = true)
public class OffsetDateTimeConverter implements AttributeConverter<OffsetDateTime, OffsetDateTime> {

    @Override
    public OffsetDateTime convertToDatabaseColumn(OffsetDateTime attribute) {
        return attribute;
    }

    @Override
    public OffsetDateTime convertToEntityAttribute(OffsetDateTime dbData) {
        return dbData == null ? null : dbData.withOffsetSameInstant(Kst.OFFSET);
    }
}
