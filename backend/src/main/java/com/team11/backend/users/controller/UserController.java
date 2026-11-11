package com.team11.backend.users.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.users.dto.ConsentsResponse;
import com.team11.backend.users.dto.DeleteAccountRequest;
import com.team11.backend.users.dto.NotificationSettingsView;
import com.team11.backend.users.dto.OnboardingRequest;
import com.team11.backend.users.dto.OnboardingResponse;
import com.team11.backend.users.dto.SelectCharacterRequest;
import com.team11.backend.users.dto.SelectCharacterResponse;
import com.team11.backend.users.dto.UpdateConsentsRequest;
import com.team11.backend.users.dto.UpdateNotificationSettingsRequest;
import com.team11.backend.users.dto.UpdateProfileRequest;
import com.team11.backend.users.dto.UserProfileResponse;
import com.team11.backend.users.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(userId)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@AuthenticationPrincipal Long userId,
                                                                            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateProfile(userId, request)));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<ApiResponse<OnboardingResponse>> onboarding(@AuthenticationPrincipal Long userId,
                                                                        @Valid @RequestBody OnboardingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.onboarding(userId, request)));
    }

    @GetMapping("/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsView>> getNotificationSettings(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getNotificationSettings(userId)));
    }

    @PutMapping("/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsView>> updateNotificationSettings(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateNotificationSettings(userId, request)));
    }

    @GetMapping("/consents")
    public ResponseEntity<ApiResponse<ConsentsResponse>> getConsents(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getConsents(userId)));
    }

    @PutMapping("/consents")
    public ResponseEntity<ApiResponse<ConsentsResponse>> updateConsents(@AuthenticationPrincipal Long userId,
                                                                          @Valid @RequestBody UpdateConsentsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateConsents(userId, request)));
    }

    @PutMapping("/character")
    public ResponseEntity<ApiResponse<SelectCharacterResponse>> selectCharacter(@AuthenticationPrincipal Long userId,
                                                                                  @Valid @RequestBody SelectCharacterRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.selectCharacter(userId, request)));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(userId, request);
        return ResponseEntity.noContent().build();
    }
}
