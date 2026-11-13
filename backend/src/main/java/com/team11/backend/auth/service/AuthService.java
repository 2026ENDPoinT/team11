package com.team11.backend.auth.service;

import com.team11.backend.auth.domain.RefreshToken;
import com.team11.backend.auth.dto.ChangePasswordRequest;
import com.team11.backend.auth.dto.LoginRequest;
import com.team11.backend.auth.dto.LoginResponse;
import com.team11.backend.auth.dto.LogoutRequest;
import com.team11.backend.auth.dto.RefreshRequest;
import com.team11.backend.auth.dto.SignupRequest;
import com.team11.backend.auth.dto.SignupResponse;
import com.team11.backend.auth.dto.TokenResponse;
import com.team11.backend.auth.repository.RefreshTokenRepository;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.config.JwtTokenProvider;
import com.team11.backend.users.domain.User;
import com.team11.backend.users.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new ApiException(ErrorCode.USER_DUPLICATE_LOGIN_ID);
        }
        User user = userRepository.save(new User(request.loginId(), passwordEncoder.encode(request.password()),
                request.name().trim()));
        return new SignupResponse(user.getId(), user.getLoginId(), user.getName());
    }

    /**
     * 아이디/비번 어느 쪽이 틀렸는지 숨긴다. 5회 연속 실패 시 5분 잠금(0-1 로그인 실패 화면).
     * 실패 예외로 트랜잭션이 롤백되면 실패 횟수 증가가 사라지므로 ApiException은 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        Instant now = Instant.now();
        if (user.isLocked(now)) {
            throw lockedException(user);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(MAX_FAILED_LOGIN_ATTEMPTS, now.plus(LOGIN_LOCK_DURATION));
            if (user.isLocked(Instant.now())) {
                throw lockedException(user);
            }
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        user.registerSuccessfulLogin();

        TokenResponse tokens = issueTokens(user.getId()).tokens();
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(),
                new LoginResponse.LoginUser(user.getId(), user.getName(), user.isOnboardingDone()));
    }

    /** 회전 방식. 이미 무효화된 토큰이 다시 오면 탈취로 간주해 그 사용자의 살아있는 refresh 토큰을 전부 무효화한다. */
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = parseRefreshClaims(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));

        Instant now = Instant.now();
        if (stored.isRevoked()) {
            revokeAllActiveTokensExcept(stored.getUserId(), null, now);
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        if (stored.isExpired(now)) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        Issued issued = issueTokens(jwtTokenProvider.getUserId(claims));
        stored.revoke(now, issued.refreshTokenId());
        return issued.tokens();
    }

    public void logout(Long userId, LogoutRequest request) {
        refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .filter(token -> token.getUserId().equals(userId))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHORIZED));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.USER_PASSWORD_MISMATCH);
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw ApiException.invalid("newPassword", "현재 비밀번호와 달라야 해요.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        String keepHash = request.refreshToken() == null ? null : hash(request.refreshToken());
        revokeAllActiveTokensExcept(userId, keepHash, Instant.now());
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
        return new Issued(new TokenResponse(accessToken, refreshToken, jwtTokenProvider.accessTokenExpirySeconds()),
                saved.getId());
    }

    private static String hash(String token) {
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
