package com.team11.backend.users.service;

import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.users.domain.ConsentType;
import com.team11.backend.users.domain.NotificationSettings;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.dto.ConsentItem;
import com.team11.backend.users.dto.ConsentsResponse;
import com.team11.backend.users.dto.ConsentsResponse.ConsentView;
import com.team11.backend.users.dto.DeleteAccountRequest;
import com.team11.backend.users.dto.NotificationSettingsView;
import com.team11.backend.users.dto.OnboardingRequest;
import com.team11.backend.users.dto.SleepSettingsView;
import com.team11.backend.users.dto.UpdateConsentsRequest;
import com.team11.backend.users.dto.UpdateNotificationSettingsRequest;
import com.team11.backend.users.dto.UpdateProfileRequest;
import com.team11.backend.users.dto.UpdateSettingsRequest;
import com.team11.backend.users.dto.UserProfileResponse;
import com.team11.backend.users.repository.NotificationSettingsRepository;
import com.team11.backend.users.repository.UserRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private static final Set<Integer> BEDTIME_LEAD_OPTIONS = Set.of(15, 30, 60);

    private final UserRepository userRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, NotificationSettingsRepository notificationSettingsRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(findUser(userId));
    }

    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.rename(request.name().trim());
        return UserProfileResponse.from(user);
    }

    /** 필수 동의 3종이 모두 true여야 한다. 알림 허용은 NOTIFICATION 동의 + 알림 설정 초기값으로 반영. */
    public UserProfileResponse onboarding(Long userId, OnboardingRequest request) {
        User user = findUser(userId);
        Map<ConsentType, Boolean> consents = toMap(request.consents());
        for (ConsentType type : ConsentType.values()) {
            if (type.isRequired() && !Boolean.TRUE.equals(consents.get(type))) {
                throw new ApiException(ErrorCode.CONSENT_REQUIRED, Map.of("missing", type.name()));
            }
        }
        if (request.notificationAllowed() != null) {
            consents.put(ConsentType.NOTIFICATION, request.notificationAllowed());
        }
        consents.forEach(user::setConsent);

        user.updateSettings(request.sleepNeedMin(), request.sleepLatencyMin(), request.commuteMin(), request.prepMin(),
                null, null, request.examPrepDays(), null, null);
        validateSettings(user);
        user.completeOnboarding();

        if (request.notificationAllowed() != null) {
            boolean allowed = request.notificationAllowed();
            getOrCreateNotificationSettings(userId).update(allowed, allowed, 30);
        }
        return UserProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public SleepSettingsView getSettings(Long userId) {
        return SleepSettingsView.from(findUser(userId));
    }

    public SleepSettingsView updateSettings(Long userId, UpdateSettingsRequest request) {
        if (request.isEmpty()) {
            throw ApiException.invalid("request", "수정할 값을 하나 이상 보내 주세요.");
        }
        User user = findUser(userId);
        user.updateSettings(request.sleepNeedMin(), request.sleepLatencyMin(), request.commuteMin(), request.prepMin(),
                request.bufferMin(), request.minSleepMin(), request.examPrepDays(), request.focusMinInBedMin(),
                request.focusMaxInBedMin());
        validateSettings(user);
        return SleepSettingsView.from(user);
    }

    @Transactional(readOnly = true)
    public ConsentsResponse getConsents(Long userId) {
        return toConsentsResponse(findUser(userId));
    }

    public ConsentsResponse updateConsents(Long userId, UpdateConsentsRequest request) {
        User user = findUser(userId);
        Map<ConsentType, Boolean> requested = toMap(request.consents());
        requested.forEach((type, agreed) -> {
            if (type.isRequired() && !agreed) {
                throw new ApiException(ErrorCode.CONSENT_NOT_REVOCABLE, Map.of("type", type.name()));
            }
        });
        requested.forEach(user::setConsent);
        return toConsentsResponse(user);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsView getNotificationSettings(Long userId) {
        findUser(userId);
        return NotificationSettingsView.from(notificationSettingsRepository.findById(userId)
                .orElseGet(() -> new NotificationSettings(userId)));
    }

    public NotificationSettingsView updateNotificationSettings(Long userId, UpdateNotificationSettingsRequest request) {
        findUser(userId);
        if (!BEDTIME_LEAD_OPTIONS.contains(request.bedtimeLeadMin())) {
            throw ApiException.invalid("bedtimeLeadMin", "15, 30, 60 중 하나여야 해요.");
        }
        NotificationSettings settings = getOrCreateNotificationSettings(userId);
        settings.update(request.wakeAlarmEnabled(), request.bedtimeAlarmEnabled(), request.bedtimeLeadMin());
        return NotificationSettingsView.from(settings);
    }

    /** 비번 확인 후 users row 삭제 — 나머지 테이블은 FK ON DELETE CASCADE. accessToken은 만료까지 남지만 사용자 조회가 실패해 막힌다. */
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        userRepository.delete(user);
    }

    private void validateSettings(User user) {
        if (user.getSleepNeedMin() < user.getMinSleepMin()) {
            throw ApiException.invalid("sleepNeedMin", "minSleepMin 이상이어야 해요.");
        }
        if (user.getFocusMinInBedMin() > user.getFocusMaxInBedMin()) {
            throw ApiException.invalid("focusMinInBedMin", "focusMaxInBedMin 이하여야 해요.");
        }
    }

    private static Map<ConsentType, Boolean> toMap(List<ConsentItem> items) {
        Map<ConsentType, Boolean> result = new EnumMap<>(ConsentType.class);
        for (ConsentItem item : items) {
            if (result.put(item.type(), item.agreed()) != null) {
                throw ApiException.invalid("consents", "같은 동의 타입이 중복됐어요: " + item.type());
            }
        }
        return result;
    }

    private static ConsentsResponse toConsentsResponse(User user) {
        return new ConsentsResponse(user.getConsents().stream().map(ConsentView::from).toList());
    }

    private NotificationSettings getOrCreateNotificationSettings(Long userId) {
        return notificationSettingsRepository.findById(userId)
                .orElseGet(() -> notificationSettingsRepository.save(new NotificationSettings(userId)));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
    }
}
