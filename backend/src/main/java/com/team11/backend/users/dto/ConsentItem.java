package com.team11.backend.users.dto;

import com.team11.backend.users.domain.ConsentType;
import jakarta.validation.constraints.NotNull;

public record ConsentItem(@NotNull ConsentType type, @NotNull Boolean agreed) {
}
