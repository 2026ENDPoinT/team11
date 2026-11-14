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
import tools.jackson.databind.ObjectMapper;

/** AI 서버(AI-02/03/06) 호출을 한 곳에 모은다. 개인 식별 정보는 어떤 요청에도 넣지 않는다. */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final String TONIGHT_PATH = "/ai/v1/plan/tonight";
    private static final String EXAM_PATH = "/ai/v1/plan/exam-sleep";
    private static final String FOCUS_PATH = "/ai/v1/plan/focus-sleep";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient tonightClient;
    private final RestClient examClient;
    private final RestClient focusClient;

    public AiClient(AiProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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
                ErrorBody error = parseError(e.getResponseBodyAsByteArray());
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

    /**
     * AI 서버는 단일 Content-Length를 요구하고(chunked 거절, 400) 응답 Content-Type이 octet-stream으로 오는 경우가 있다.
     * 그래서 본문은 직접 직렬화한 byte[]로 보내고(길이가 붙음), 응답도 바이트로 받아 직접 파싱한다.
     */
    private <T> T post(RestClient client, String path, Object body, Class<T> type) {
        byte[] response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsBytes(body)).retrieve().body(byte[].class);
        return response == null || response.length == 0 ? null : objectMapper.readValue(response, type);
    }

    private ErrorBody parseError(byte[] body) {
        try {
            return body == null || body.length == 0 ? null : objectMapper.readValue(body, ErrorBody.class);
        } catch (Exception e) {
            return null;
        }
    }

    record ErrorBody(ErrorDetail error) {
        record ErrorDetail(String code, String message) {
        }
    }
}
