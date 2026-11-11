package com.team11.backend.users.service;

import com.team11.backend.auth.dto.ConsentItem;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.FieldViolation;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.users.domain.Character;
import com.team11.backend.users.domain.Chronotype;
import com.team11.backend.users.domain.Consent;
import com.team11.backend.users.domain.ConsentType;
import com.team11.backend.users.domain.NotificationSettings;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.dto.CharacterListResponse;
import com.team11.backend.users.dto.CharacterView;
import com.team11.backend.users.dto.ConsentsResponse;
import com.team11.backend.users.dto.ConsentsResponse.ConsentView;
import com.team11.backend.users.dto.DeleteAccountRequest;
import com.team11.backend.users.dto.NotificationSettingsView;
import com.team11.backend.users.dto.OnboardingRequest;
import com.team11.backend.users.dto.OnboardingResponse;
import com.team11.backend.users.dto.OnboardingResponse.SuggestedSettings;
import com.team11.backend.users.dto.SelectCharacterRequest;
import com.team11.backend.users.dto.SelectCharacterResponse;
import com.team11.backend.users.dto.UpdateConsentsRequest;
import com.team11.backend.users.dto.UpdateNotificationSettingsRequest;
import com.team11.backend.users.dto.UpdateProfileRequest;
import com.team11.backend.users.dto.UserProfileResponse;
import com.team11.backend.users.repository.CharacterRepository;
import com.team11.backend.users.repository.NotificationSettingsRepository;
import com.team11.backend.users.repository.UserRepository;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CHRONOTYPE_MORNING_BOUNDARY_MIN = 4 * 60;
    private static final int CHRONOTYPE_NEUTRAL_BOUNDARY_MIN = 5 * 60 + 30;

    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, CharacterRepository characterRepository,
                        NotificationSettingsRepository notificationSettingsRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.characterRepository = characterRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(findUser(userId));
    }

    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("request", "수정할 값을 하나 이상 보내 주세요.")));
        }
        User user = findUser(userId);

        if (request.nickname() != null && userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
            throw new ApiException(ErrorCode.NICKNAME_DUPLICATED);
        }

        int effectiveSleepNeedMin = request.sleepNeedMin() != null ? request.sleepNeedMin() : user.getSleepNeedMin();
        int effectiveMinSleepMin = request.minSleepMin() != null ? request.minSleepMin() : user.getMinSleepMin();
        if (effectiveSleepNeedMin < effectiveMinSleepMin) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("sleepNeedMin", "minSleepMin 이상이어야 해요.")));
        }

        user.updateSettings(request.nickname(), request.chronotype(), request.sleepNeedMin(),
                request.sleepLatencyMin(), request.commuteMin(), request.prepMin(), request.bufferMin(),
                request.minSleepMin());
        return UserProfileResponse.from(user);
    }

    public OnboardingResponse onboarding(Long userId, OnboardingRequest request) {
        User user = findUser(userId);

        // 평일 시각은 형식만 검증하고 계산에는 쓰지 않음(명세가 자유일만 사용)
        parseTime("weekdayBedTime", request.weekdayBedTime());
        parseTime("weekdayWakeTime", request.weekdayWakeTime());
        LocalTime bed = parseTime("freedayBedTime", request.freedayBedTime());
        LocalTime wake = parseTime("freedayWakeTime", request.freedayWakeTime());

        int bedMin = bed.getHour() * 60 + bed.getMinute();
        int wakeMin = wake.getHour() * 60 + wake.getMinute();
        int durationMin = bedMin <= wakeMin ? wakeMin - bedMin : (1440 - bedMin) + wakeMin;
        int midMin = (bedMin + durationMin / 2) % 1440;
        Chronotype chronotype = classifyChronotype(midMin);

        int suggestedSleepNeedMin = Math.clamp(durationMin, 300, 600);

        user.applyOnboarding(chronotype, suggestedSleepNeedMin, request.sleepLatencyMin(), request.commuteMin(),
                request.prepMin());

        String midSleepFreeday = "%02d:%02d".formatted(midMin / 60, midMin % 60);
        SuggestedSettings suggestedSettings = new SuggestedSettings(
                suggestedSleepNeedMin, request.sleepLatencyMin(), request.commuteMin(), request.prepMin(), 10);
        return new OnboardingResponse(chronotype, midSleepFreeday, suggestedSettings, user.isOnboardingDone());
    }

    @Transactional(readOnly = true)
    public NotificationSettingsView getNotificationSettings(Long userId) {
        return NotificationSettingsView.from(getOrCreateNotificationSettings(userId));
    }

    public NotificationSettingsView updateNotificationSettings(Long userId, UpdateNotificationSettingsRequest request) {
        NotificationSettings settings = getOrCreateNotificationSettings(userId);
        LocalTime quietStart = null;
        LocalTime quietEnd = null;
        if (request.quietHours() != null) {
            quietStart = parseTime("quietHours.start", request.quietHours().start());
            quietEnd = parseTime("quietHours.end", request.quietHours().end());
        }
        settings.update(request.enabled(), request.windDownMinBefore(), request.bedReminderEnabled(),
                request.caffeineCutoffEnabled(), request.napReminderEnabled(), request.streakRiskEnabled(),
                request.weeklyReportEnabled(), quietStart, quietEnd);
        return NotificationSettingsView.from(settings);
    }

    @Transactional(readOnly = true)
    public ConsentsResponse getConsents(Long userId) {
        User user = findUser(userId);
        return new ConsentsResponse(user.getConsents().stream().map(ConsentView::from).toList());
    }

    public ConsentsResponse updateConsents(Long userId, UpdateConsentsRequest request) {
        User user = findUser(userId);
        Map<ConsentType, Boolean> requested = new EnumMap<>(ConsentType.class);
        for (ConsentItem item : request.consents()) {
            ConsentType type = toConsentType(item.type());
            if (requested.containsKey(type)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        List.of(new FieldViolation("consents", "같은 동의 타입이 중복됐어요: " + item.type())));
            }
            if (type.isRequired() && !Boolean.TRUE.equals(item.agreed())) {
                throw new ApiException(ErrorCode.CONSENT_NOT_REVOCABLE);
            }
            requested.put(type, item.agreed());
        }
        for (Consent consent : user.getConsents()) {
            Boolean agreed = requested.get(consent.getType());
            if (agreed != null) {
                consent.updateAgreement(agreed);
            }
        }
        return new ConsentsResponse(user.getConsents().stream().map(ConsentView::from).toList());
    }

    public CharacterListResponse listCharacters(Long userId) {
        User user = findUser(userId);
        List<CharacterView> characters = characterRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(CharacterView::from)
                .toList();
        Long selectedId = user.getCharacter() != null ? user.getCharacter().getId() : null;
        return new CharacterListResponse(selectedId, characters);
    }

    public SelectCharacterResponse selectCharacter(Long userId, SelectCharacterRequest request) {
        User user = findUser(userId);
        Character character = characterRepository.findById(request.characterId())
                .filter(Character::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.CHARACTER_NOT_FOUND));
        user.selectCharacter(character);
        return new SelectCharacterResponse(CharacterView.from(character));
    }

    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        userRepository.delete(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
    }

    private NotificationSettings getOrCreateNotificationSettings(Long userId) {
        return notificationSettingsRepository.findById(userId)
                .orElseGet(() -> notificationSettingsRepository.save(new NotificationSettings(userId)));
    }

    private ConsentType toConsentType(String type) {
        try {
            return ConsentType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("consents", "알 수 없는 동의 타입이에요: " + type)));
        }
    }

    private LocalTime parseTime(String field, String value) {
        try {
            return LocalTime.parse(value, HH_MM);
        } catch (DateTimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation(field, "HH:mm 형식이어야 해요.")));
        }
    }

    private Chronotype classifyChronotype(int midSleepMin) {
        if (midSleepMin < CHRONOTYPE_MORNING_BOUNDARY_MIN) {
            return Chronotype.MORNING;
        }
        if (midSleepMin <= CHRONOTYPE_NEUTRAL_BOUNDARY_MIN) {
            return Chronotype.NEUTRAL;
        }
        return Chronotype.EVENING;
    }
}
