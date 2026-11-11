package com.team11.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsentItem(@NotBlank String type, @NotNull Boolean agreed) {
}
