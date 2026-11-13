package com.team11.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 0-2 회원가입 화면: 이름·아이디·비밀번호. 동의는 온보딩(POST /users/me/onboarding)에서 받는다. */
public record SignupRequest(
        @NotBlank @Size(max = 30, message = "30자 이내여야 해요.") String name,
        @Pattern(regexp = "^[a-z0-9]{4,20}$", message = "4~20자, 영문 소문자와 숫자만 사용할 수 있어요.") String loginId,
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "8자 이상, 영문과 숫자를 포함해야 해요.") String password
) {
}
