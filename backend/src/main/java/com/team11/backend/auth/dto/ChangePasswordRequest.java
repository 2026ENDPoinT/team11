package com.team11.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** refreshToken(선택)을 보내면 그 기기 세션만 남기고 나머지를 로그아웃시킨다. 안 보내면 전체 로그아웃. */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "8자 이상, 영문과 숫자를 포함해야 해요.") String newPassword,
        String refreshToken
) {
}
