package com.team11.backend.users.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String loginId;
    private String passwordHash;
    private String nickname;
    private boolean onboardingDone;

    @Enumerated(EnumType.STRING)
    private Chronotype chronotype = Chronotype.NEUTRAL;

    private int sleepNeedMin = 465;
    private int sleepLatencyMin = 15;
    private int commuteMin = 0;
    private int prepMin = 0;
    private int bufferMin = 10;
    private int minSleepMin = 300;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    private Character character;

    private int failedLoginCount;
    private Instant lockedUntil;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Consent> consents = new ArrayList<>();

    public User(String loginId, String passwordHash, String nickname, Character character) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.character = character;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(Instant now, int maxAttempts, Instant lockUntil) {
        this.failedLoginCount++;
        if (this.failedLoginCount >= maxAttempts) {
            this.failedLoginCount = 0;
            this.lockedUntil = lockUntil;
        }
    }

    public void registerSuccessfulLogin() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void addConsent(Consent consent) {
        consents.add(consent);
        consent.assignTo(this);
    }

    public boolean hasAgreedTo(ConsentType type) {
        return consents.stream().anyMatch(c -> c.getType() == type && c.isAgreed());
    }

    /** PATCH /users/me — 보낸 필드만 수정(null은 미변경). */
    public void updateSettings(String nickname, Chronotype chronotype, Integer sleepNeedMin, Integer sleepLatencyMin,
                                Integer commuteMin, Integer prepMin, Integer bufferMin, Integer minSleepMin) {
        if (nickname != null) this.nickname = nickname;
        if (chronotype != null) this.chronotype = chronotype;
        if (sleepNeedMin != null) this.sleepNeedMin = sleepNeedMin;
        if (sleepLatencyMin != null) this.sleepLatencyMin = sleepLatencyMin;
        if (commuteMin != null) this.commuteMin = commuteMin;
        if (prepMin != null) this.prepMin = prepMin;
        if (bufferMin != null) this.bufferMin = bufferMin;
        if (minSleepMin != null) this.minSleepMin = minSleepMin;
    }

    public void applyOnboarding(Chronotype chronotype, int sleepNeedMin, int sleepLatencyMin, int commuteMin, int prepMin) {
        this.chronotype = chronotype;
        this.sleepNeedMin = sleepNeedMin;
        this.sleepLatencyMin = sleepLatencyMin;
        this.commuteMin = commuteMin;
        this.prepMin = prepMin;
        this.onboardingDone = true;
    }

    public void selectCharacter(Character character) {
        this.character = character;
    }
}
