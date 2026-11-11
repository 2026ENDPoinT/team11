package com.team11.backend.users.dto;

import com.team11.backend.users.domain.Consent;
import java.time.Instant;
import java.util.List;

public record ConsentsResponse(List<ConsentView> consents) {

    public record ConsentView(String type, boolean required, boolean agreed, Instant agreedAt) {

        public static ConsentView from(Consent consent) {
            return new ConsentView(consent.getType().name(), consent.getType().isRequired(),
                    consent.isAgreed(), consent.getAgreedAt());
        }
    }
}
