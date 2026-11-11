package com.team11.backend.home.controller;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.home.dto.HomeResponse;
import com.team11.backend.home.service.HomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/api/v1/home")
    public ResponseEntity<ApiResponse<HomeResponse>> getHome(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(homeService.getHome(userId)));
    }
}
