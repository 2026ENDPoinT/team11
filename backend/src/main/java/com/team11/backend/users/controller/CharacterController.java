package com.team11.backend.users.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.users.dto.CharacterListResponse;
import com.team11.backend.users.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/characters")
public class CharacterController {

    private final UserService userService;

    public CharacterController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CharacterListResponse>> listCharacters(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.listCharacters(userId)));
    }
}
