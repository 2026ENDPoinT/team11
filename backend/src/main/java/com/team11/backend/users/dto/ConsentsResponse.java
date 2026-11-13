package com.team11.backend.users.dto;

import com.team11.backend.users.domain.Consent;
import com.team11.backend.users.domain.ConsentType;
import java.time.Instant;
import java.util.List;

public record ConsentsResponse(List<ConsentView> consents) {

    public record ConsentView(ConsentType type, boolean required, boolean agreed, Instant agreedAt) {

        public static ConsentView from(Consent c) {
            return new ConsentView(c.getType(), c.getType().isRequired(), c.isAgreed(), c.getAgreedAt());
        }
    }
}
