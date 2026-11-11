package com.team11.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,

        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "8자 이상, 영문과 숫자를 포함해야 해요.")
        String newPassword,

        /** 변경을 요청한 이 기기의 refreshToken. 주면 이 토큰만 남기고 나머지 기기를 로그아웃시킨다(명세 AUTH-06 "변경한 기기의 세션은 유지"). 없으면 전체 무효화. */
        String refreshToken
) {
}
