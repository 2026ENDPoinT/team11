package com.team11.backend.users.dto;

import java.util.List;

public record CharacterListResponse(Long selectedCharacterId, List<CharacterView> characters) {
}
