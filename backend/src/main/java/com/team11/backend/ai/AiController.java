package com.team11.backend.ai;

import com.team11.backend.common.ApiResponse;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import com.team11.backend.users.domain.ConsentType;
import com.team11.backend.users.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiClient aiClient;
    private final UserRepository userRepository;

    public AiController(AiClient aiClient, UserRepository userRepository) {
        this.aiClient = aiClient;
        this.userRepository = userRepository;
    }

    @GetMapping("/health")
    public ApiResponse<JsonNode> health() {
        return ApiResponse.success(aiClient.health());
    }

    @PostMapping("/plan/tonight")
    public ApiResponse<JsonNode> planTonight(@AuthenticationPrincipal Long userId, @RequestBody JsonNode request) {
        requireHealthDataConsent(userId);
        return ApiResponse.success(aiClient.planTonight(request));
    }

    @PostMapping("/plan/exam-sleep")
    public ApiResponse<JsonNode> examSleep(@AuthenticationPrincipal Long userId, @RequestBody JsonNode request) {
        requireHealthDataConsent(userId);
        return ApiResponse.success(aiClient.examSleep(request));
    }

    @PostMapping("/sleep-debt/calculate")
    public ApiResponse<JsonNode> sleepDebt(@AuthenticationPrincipal Long userId, @RequestBody JsonNode request) {
        requireHealthDataConsent(userId);
        return ApiResponse.success(aiClient.sleepDebt(request));
    }

    @PostMapping("/insights/risks")
    public ApiResponse<JsonNode> scheduleRisks(@RequestBody JsonNode request) {
        return ApiResponse.success(aiClient.scheduleRisks(request));
    }

    @PostMapping("/plan/focus-sleep")
    public ApiResponse<JsonNode> focusSleep(@AuthenticationPrincipal Long userId, @RequestBody JsonNode request) {
        requireHealthDataConsent(userId);
        return ApiResponse.success(aiClient.focusSleep(request));
    }

    private void requireHealthDataConsent(Long userId) {
        if (!userRepository.existsByIdAndConsentsTypeAndConsentsAgreedTrue(userId, ConsentType.HEALTH_DATA)) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED);
        }
    }
}
