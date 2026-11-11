package com.team11.backend.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SignupRequest(
        @Pattern(regexp = "^[a-z0-9]{4,20}$", message = "4~20자, 영문 소문자와 숫자만 사용할 수 있어요.")
        String loginId,

        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "8자 이상, 영문과 숫자를 포함해야 해요.")
        String password,

        @Size(min = 2, max = 12, message = "2~12자여야 해요.")
        String nickname,

        @NotEmpty(message = "동의 항목이 필요해요.")
        List<@Valid ConsentItem> consents
) {
}
