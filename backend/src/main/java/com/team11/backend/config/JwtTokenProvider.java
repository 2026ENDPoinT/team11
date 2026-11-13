package com.team11.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public long accessTokenExpirySeconds() {
        return properties.accessTokenExpirySeconds();
    }

    public String createAccessToken(Long userId) {
        return create(userId, TYPE_ACCESS, Instant.now().plusSeconds(properties.accessTokenExpirySeconds()));
    }

    public String createRefreshToken(Long userId, Instant expiresAt) {
        return create(userId, TYPE_REFRESH, expiresAt);
    }

    public Instant refreshTokenExpiresAt() {
        return Instant.now().plus(Duration.ofDays(properties.refreshTokenExpiryDays()));
    }

    private String create(Long userId, String type, Instant expiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString()) // 같은 초에 발급돼도 토큰 문자열이 겹치지 않도록
                .claim(CLAIM_TYPE, type)
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증. 만료는 ExpiredJwtException, 그 외 위조·형식 오류는 JwtException. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
