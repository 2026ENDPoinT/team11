package com.team11.backend.ai;

import com.team11.backend.ai.dto.AiPlanTonightRequest;
import com.team11.backend.ai.dto.AiPlanTonightResult;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** AI 서버 호출을 한 곳에 모은다. 실패하면(설정 없음·타임아웃·오류 응답 전부) 예외를 던지지 않고 비워서 돌려준다 — 호출부는 항상 규칙 폴백으로 넘어가면 된다. */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final String PLAN_TONIGHT_PATH = "/ai/v1/plan/tonight";

    private final AiProperties properties;
    private final RestClient restClient;

    public AiClient(AiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(properties.timeoutSeconds()).toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder()
                .baseUrl(properties.isConfigured() ? properties.baseUrl() : "http://ai-not-configured.invalid")
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<AiPlanTonightResult> planTonight(AiPlanTonightRequest request) {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }
        try {
            AiPlanTonightResult result = restClient.post()
                    .uri(PLAN_TONIGHT_PATH)
                    .header("X-Internal-Key", properties.internalKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiPlanTonightResult.class);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("AI {} 호출 실패, 규칙 폴백으로 진행: {}", PLAN_TONIGHT_PATH, e.toString());
            return Optional.empty();
        }
    }
}
