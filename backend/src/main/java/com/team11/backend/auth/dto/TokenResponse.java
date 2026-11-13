package com.team11.backend.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
