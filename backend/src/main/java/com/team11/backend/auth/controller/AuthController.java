package com.team11.backend.auth.controller;

import com.team11.backend.auth.dto.ChangePasswordRequest;
import com.team11.backend.auth.dto.CheckLoginIdResponse;
import com.team11.backend.auth.dto.LoginRequest;
import com.team11.backend.auth.dto.LoginResponse;
import com.team11.backend.auth.dto.LogoutRequest;
import com.team11.backend.auth.dto.RefreshRequest;
import com.team11.backend.auth.dto.SignupRequest;
import com.team11.backend.auth.dto.SignupResponse;
import com.team11.backend.auth.dto.TokenResponse;
import com.team11.backend.auth.service.AuthService;
import com.team11.backend.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId, @Valid @RequestBody LogoutRequest request) {
        authService.logout(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check-login-id")
    public ResponseEntity<ApiResponse<CheckLoginIdResponse>> checkLoginId(
            @RequestParam @Pattern(regexp = "^[a-z0-9]{4,20}$", message = "4~20자, 영문 소문자와 숫자만 사용할 수 있어요.") String loginId) {
        return ResponseEntity.ok(ApiResponse.success(authService.checkLoginId(loginId)));
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Long userId,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}
