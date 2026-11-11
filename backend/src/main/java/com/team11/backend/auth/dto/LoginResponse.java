package com.team11.backend.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, long expiresIn, LoginUser user) {

    public record LoginUser(Long userId, String nickname, boolean onboardingDone) {
    }
}
