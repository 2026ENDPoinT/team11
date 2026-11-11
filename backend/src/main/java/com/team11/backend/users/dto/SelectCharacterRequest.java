package com.team11.backend.users.dto;

import jakarta.validation.constraints.NotNull;

public record SelectCharacterRequest(@NotNull Long characterId) {
}
