package com.team11.backend.users.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "consents")
@Getter
@NoArgsConstructor
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private ConsentType type;

    private boolean agreed;
    private Instant agreedAt;

    public Consent(ConsentType type, boolean agreed) {
        this.type = type;
        this.agreed = agreed;
        this.agreedAt = agreed ? Instant.now() : null;
    }

    void assignTo(User user) {
        this.user = user;
    }

    public void updateAgreement(boolean agreed) {
        this.agreed = agreed;
        this.agreedAt = agreed ? Instant.now() : null;
    }
}
