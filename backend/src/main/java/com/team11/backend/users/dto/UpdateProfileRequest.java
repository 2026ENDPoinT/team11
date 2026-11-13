package com.team11.backend.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@NotBlank @Size(max = 30, message = "30자 이내여야 해요.") String name) {
}
