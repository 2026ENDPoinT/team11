package com.team11.backend.ai;

import com.team11.backend.ai.dto.AiExamSleepRequest;
import com.team11.backend.ai.dto.AiExamSleepResult;
import com.team11.backend.ai.dto.AiFocusSleepRequest;
import com.team11.backend.ai.dto.AiFocusSleepResult;
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

/** AI 서버(AI-02/03/06) 호출을 한 곳에 모은다. 개인 식별 정보는 어떤 요청에도 넣지 않는다. */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final String TONIGHT_PATH = "/ai/v1/plan/tonight";
    private static final String EXAM_PATH = "/ai/v1/plan/exam-sleep";
    private static final String FOCUS_PATH = "/ai/v1/plan/focus-sleep";

    private final AiProperties properties;
    private final RestClient tonightClient;
    private final RestClient examClient;
    private final RestClient focusClient;

    public AiClient(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.tonightClient = build(builder, properties.timeout().tonightSeconds());
        this.examClient = build(builder, properties.timeout().examSeconds());
        this.focusClient = build(builder, properties.timeout().focusSeconds());
    }

    private RestClient build(RestClient.Builder builder, long readTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(readTimeoutSeconds).toMillis());
        return builder.clone()
                .baseUrl(properties.isConfigured() ? properties.baseUrl() : "http://ai-not-configured.invalid")
                .defaultHeader("X-Internal-Key", properties.internalKey() == null ? "" : properties.internalKey())
                .requestFactory(factory)
                .build();
    }

    /** AI-02. 실패(미설정·타임아웃·오류 응답·422)는 전부 비워서 돌려준다 — 호출부가 규칙 폴백으로 간다. */
    public Optional<AiPlanTonightResult> planTonight(AiPlanTonightRequest request) {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(post(tonightClient, TONIGHT_PATH, request, AiPlanTonightResult.class));
        } catch (Exception e) {
            log.warn("AI {} 호출 실패, 규칙 폴백으로 진행: {}", TONIGHT_PATH, e.toString());
            return Optional.empty();
        }
    }

    /** AI-03. 대체 규칙이 없어 실패는 AI_UNAVAILABLE(503), 422는 {@link AiRejectedException}으로 던진다. */
    public AiExamSleepResult examSleepPlan(AiExamSleepRequest request) {
        return postOrThrow(examClient, EXAM_PATH, request, AiExamSleepResult.class);
    }

    /** AI-06. 실패 처리는 AI-03과 동일. */
    public AiFocusSleepResult focusSleepPlan(AiFocusSleepRequest request) {
        return postOrThrow(focusClient, FOCUS_PATH, request, AiFocusSleepResult.class);
    }

    private <T> T postOrThrow(RestClient client, String path, Object body, Class<T> type) {
        if (!properties.isConfigured()) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        try {
            T result = post(client, path, body, type);
            if (result == null) {
                throw new ApiException(ErrorCode.AI_UNAVAILABLE);
            }
            return result;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_CONTENT) {
                ErrorBody error = e.getResponseBodyAs(ErrorBody.class);
                String code = error != null && error.error() != null ? error.error().code() : "REJECTED";
                String message = error != null && error.error() != null ? error.error().message() : e.getMessage();
                throw new AiRejectedException(code, message);
            }
            log.warn("AI {} 호출 실패: {}", path, e.toString());
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        } catch (ApiException | AiRejectedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI {} 호출 실패: {}", path, e.toString());
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private <T> T post(RestClient client, String path, Object body, Class<T> type) {
        return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(type);
    }

    record ErrorBody(ErrorDetail error) {
        record ErrorDetail(String code, String message) {
        }
    }
}
