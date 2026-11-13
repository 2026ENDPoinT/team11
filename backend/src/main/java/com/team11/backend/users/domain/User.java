package com.team11.backend.users.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
    private String name;
    private boolean onboardingDone;

    // 수면 설정 시트(일상 모드) — 범위는 V1 CHECK 제약과 AI-02 profile 계약
    private int sleepNeedMin = 465;
    private int sleepLatencyMin = 15;
    private int commuteMin = 0;
    private int prepMin = 0;
    private int bufferMin = 10;
    private int minSleepMin = 300;
    private int examPrepDays = 3;
    // 집중 모드 하루 필요 수면 범위(침대 체류 분)
    private int focusMinInBedMin = 360;
    private int focusMaxInBedMin = 480;

    private int failedLoginCount;
    private Instant lockedUntil;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Consent> consents = new ArrayList<>();

    public User(String loginId, String passwordHash, String name) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(int maxAttempts, Instant lockUntil) {
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

    public void rename(String name) {
        this.name = name;
    }

    public void completeOnboarding() {
        this.onboardingDone = true;
    }

    /** 보낸 값만 바꾼다(null = 유지). 교차 검증(sleepNeed ≥ minSleep, focusMin ≤ focusMax)은 서비스에서. */
    public void updateSettings(Integer sleepNeedMin, Integer sleepLatencyMin, Integer commuteMin, Integer prepMin,
                                Integer bufferMin, Integer minSleepMin, Integer examPrepDays,
                                Integer focusMinInBedMin, Integer focusMaxInBedMin) {
        if (sleepNeedMin != null) this.sleepNeedMin = sleepNeedMin;
        if (sleepLatencyMin != null) this.sleepLatencyMin = sleepLatencyMin;
        if (commuteMin != null) this.commuteMin = commuteMin;
        if (prepMin != null) this.prepMin = prepMin;
        if (bufferMin != null) this.bufferMin = bufferMin;
        if (minSleepMin != null) this.minSleepMin = minSleepMin;
        if (examPrepDays != null) this.examPrepDays = examPrepDays;
        if (focusMinInBedMin != null) this.focusMinInBedMin = focusMinInBedMin;
        if (focusMaxInBedMin != null) this.focusMaxInBedMin = focusMaxInBedMin;
    }

    /** 동의 upsert. */
    public void setConsent(ConsentType type, boolean agreed) {
        consents.stream().filter(c -> c.getType() == type).findFirst()
                .ifPresentOrElse(c -> c.update(agreed), () -> consents.add(new Consent(this, type, agreed)));
    }

    public boolean hasAgreedTo(ConsentType type) {
        return consents.stream().anyMatch(c -> c.getType() == type && c.isAgreed());
    }
}
