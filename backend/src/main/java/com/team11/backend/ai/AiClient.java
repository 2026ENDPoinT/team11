package com.team11.backend.ai;

import com.team11.backend.ai.dto.AiExamSleepRequest;
import com.team11.backend.ai.dto.AiExamSleepResult;
import com.team11.backend.ai.dto.AiPlanTonightRequest;
import com.team11.backend.ai.dto.AiPlanTonightResult;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** AI 서버 호출을 한 곳에 모은다. */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final String PLAN_TONIGHT_PATH = "/ai/v1/plan/tonight";
    private static final String EXAM_SLEEP_PATH = "/ai/v1/plan/exam-sleep";

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

    /** 실패하면(설정 없음·타임아웃·오류 응답 전부) 예외를 던지지 않고 비워서 돌려준다 — 호출부는 항상 규칙 폴백으로 넘어가면 된다. */
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

    /**
     * AI-03. 이 추천은 AI가 없으면 대체할 규칙이 없어서(plan/tonight과 다름) 실패를 비워서 삼키지 않고
     * {@link ApiException}으로 던진다. 대상 시험 없음(422)은 {@code EVENT_NOT_FOUND}로, 그 외 실패는
     * {@code AI_UNAVAILABLE}로 변환한다.
     */
    public AiExamSleepResult examSleepPlan(AiExamSleepRequest request) {
        if (!properties.isConfigured()) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        try {
            AiExamSleepResult result = restClient.post()
                    .uri(EXAM_SLEEP_PATH)
                    .header("X-Internal-Key", properties.internalKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiExamSleepResult.class);
            if (result == null) {
                throw new ApiException(ErrorCode.AI_UNAVAILABLE);
            }
            return result;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new ApiException(ErrorCode.EVENT_NOT_FOUND);
            }
            log.warn("AI {} 호출 실패: {}", EXAM_SLEEP_PATH, e.toString());
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI {} 호출 실패: {}", EXAM_SLEEP_PATH, e.toString());
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
    }
}
