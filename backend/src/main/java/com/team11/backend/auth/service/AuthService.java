package com.team11.backend.auth.service;

import com.team11.backend.auth.domain.RefreshToken;
import com.team11.backend.users.domain.Character;
import com.team11.backend.users.domain.Consent;
import com.team11.backend.users.domain.ConsentType;
import com.team11.backend.users.domain.User;
import com.team11.backend.auth.dto.ChangePasswordRequest;
import com.team11.backend.auth.dto.CheckLoginIdResponse;
import com.team11.backend.auth.dto.ConsentItem;
import com.team11.backend.auth.dto.LoginRequest;
import com.team11.backend.auth.dto.LoginResponse;
import com.team11.backend.auth.dto.LogoutRequest;
import com.team11.backend.auth.dto.RefreshRequest;
import com.team11.backend.auth.dto.SignupRequest;
import com.team11.backend.auth.dto.SignupResponse;
import com.team11.backend.auth.dto.TokenResponse;
import com.team11.backend.auth.repository.RefreshTokenRepository;
import com.team11.backend.users.repository.CharacterRepository;
import com.team11.backend.users.repository.UserRepository;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.FieldViolation;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.config.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, CharacterRepository characterRepository,
                        RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.characterRepository = characterRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new ApiException(ErrorCode.USER_DUPLICATE_LOGIN_ID);
        }
        Map<ConsentType, Boolean> agreedByType = parseConsents(request.consents());
        if (!isAgreed(agreedByType, ConsentType.TERMS) || !isAgreed(agreedByType, ConsentType.PRIVACY)) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED);
        }

        Character defaultCharacter = characterRepository.findFirstByActiveTrueOrderBySortOrderAsc().orElse(null);
        User user = new User(request.loginId(), passwordEncoder.encode(request.password()), request.nickname(), defaultCharacter);
        for (ConsentType type : ConsentType.values()) {
            user.addConsent(new Consent(type, agreedByType.getOrDefault(type, false)));
        }
        userRepository.save(user);
        return new SignupResponse(user.getId(), user.getLoginId(), user.getNickname());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        Instant now = Instant.now();
        if (user.isLocked(now)) {
            throw lockedException(user);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(now, MAX_FAILED_LOGIN_ATTEMPTS, now.plus(LOGIN_LOCK_DURATION));
            if (user.isLocked(Instant.now())) {
                throw lockedException(user);
            }
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        user.registerSuccessfulLogin();

        Issued issued = issueTokens(user.getId());
        LoginResponse.LoginUser loginUser = new LoginResponse.LoginUser(user.getId(), user.getNickname(), user.isOnboardingDone());
        return new LoginResponse(issued.tokens().accessToken(), issued.tokens().refreshToken(), issued.tokens().expiresIn(), loginUser);
    }

    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = parseRefreshClaims(request.refreshToken());
        String hash = hash(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));

        Instant now = Instant.now();
        if (stored.isRevoked()) {
            revokeAllActiveTokens(stored.getUserId(), now);
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        if (stored.isExpired(now)) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        Long userId = jwtTokenProvider.getUserId(claims);
        Issued issued = issueTokens(userId);
        stored.revoke(now, issued.refreshTokenId());
        return issued.tokens();
    }

    public void logout(Long userId, LogoutRequest request) {
        // ponytail: deviceId 비활성화는 Devices 도메인(기기 push 토큰) 구현 후 연결, 현재는 refreshToken 무효화만 수행.
        String hash = hash(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash)
                .filter(token -> token.getUserId().equals(userId))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    @Transactional(readOnly = true)
    public CheckLoginIdResponse checkLoginId(String loginId) {
        // ponytail: IP당 분당 20회 제한은 공통 rate-limit 인프라가 생기면 필터로 추가.
        return new CheckLoginIdResponse(!userRepository.existsByLoginId(loginId));
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.USER_PASSWORD_MISMATCH);
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("newPassword", "현재 비밀번호와 달라야 해요.")));
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        String keepHash = request.refreshToken() == null ? null : hash(request.refreshToken());
        revokeAllActiveTokensExcept(userId, keepHash, Instant.now());
    }

    private Map<ConsentType, Boolean> parseConsents(List<ConsentItem> items) {
        Map<ConsentType, Boolean> result = new EnumMap<>(ConsentType.class);
        for (ConsentItem item : items) {
            ConsentType type = toConsentType(item.type());
            if (result.containsKey(type)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        List.of(new FieldViolation("consents", "같은 동의 타입이 중복됐어요: " + item.type())));
            }
            result.put(type, item.agreed());
        }
        return result;
    }

    private ConsentType toConsentType(String type) {
        try {
            return ConsentType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("consents", "알 수 없는 동의 타입이에요: " + type)));
        }
    }

    private boolean isAgreed(Map<ConsentType, Boolean> agreedByType, ConsentType type) {
        return Boolean.TRUE.equals(agreedByType.get(type));
    }

    private Claims parseRefreshClaims(String token) {
        try {
            Claims claims = jwtTokenProvider.parse(token);
            if (!jwtTokenProvider.isRefreshToken(claims)) {
                throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
            }
            return claims;
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    private ApiException lockedException(User user) {
        long retryAfterSec = Math.max(0, Duration.between(Instant.now(), user.getLockedUntil()).getSeconds());
        return new ApiException(ErrorCode.RATE_LIMITED, Map.of("retryAfterSec", retryAfterSec));
    }

    private void revokeAllActiveTokens(Long userId, Instant now) {
        revokeAllActiveTokensExcept(userId, null, now);
    }

    private void revokeAllActiveTokensExcept(Long userId, String keepTokenHash, Instant now) {
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(token -> keepTokenHash == null || !token.getTokenHash().equals(keepTokenHash))
                .forEach(token -> token.revoke(now));
    }

    private Issued issueTokens(Long userId) {
        Instant expiresAt = jwtTokenProvider.refreshTokenExpiresAt();
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, expiresAt);
        RefreshToken saved = refreshTokenRepository.save(new RefreshToken(userId, hash(refreshToken), expiresAt));
        TokenResponse response = new TokenResponse(accessToken, refreshToken, jwtTokenProvider.accessTokenExpirySeconds());
        return new Issued(response, saved.getId());
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Issued(TokenResponse tokens, Long refreshTokenId) {
    }
}
