package com.team11.backend.users.dto;

import com.team11.backend.users.domain.Character;

public record CharacterView(Long characterId, String code, String name, String description, String imageUrl) {

    public static CharacterView from(Character character) {
        if (character == null) {
            return null;
        }
        return new CharacterView(character.getId(), character.getCode(), character.getName(),
                character.getDescription(), character.getImageUrl());
    }
}
