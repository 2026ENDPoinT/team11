package com.team11.backend.ai;

import com.team11.backend.ai.dto.AiPlanTonightRequest;
import com.team11.backend.ai.dto.AiPlanTonightResult;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final String PLAN_TONIGHT_PATH = "/ai/v1/plan/tonight";

    private final AiProperties properties;
    private final RestClient restClient;
    private final RestClient longRunningClient;

    public AiClient(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = createClient(builder, properties.timeoutSeconds());
        this.longRunningClient = createClient(builder, properties.longTimeoutSeconds());
    }

    private RestClient createClient(RestClient.Builder builder, long readTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return builder.clone()
                .baseUrl(properties.isConfigured() ? properties.baseUrl() : "http://ai-not-configured.invalid")
                .requestFactory(factory)
                .bufferContent((uri, method) -> true)
                .build();
    }

    public JsonNode health() {
        String path = "/ai/v1/health";
        requireServer();
        try {
            JsonNode response = restClient.get().uri(path).retrieve().body(JsonNode.class);
            if (response != null && "ok".equals(response.path("status").asText())) {
                return response;
            }
        } catch (RestClientException error) {
            throw unavailable(path, error);
        }
        throw new ApiException(ErrorCode.AI_UNAVAILABLE);
    }

    public Optional<AiPlanTonightResult> planTonight(AiPlanTonightRequest request) {
        try {
            AiPlanTonightResult response = post(restClient, PLAN_TONIGHT_PATH, request, AiPlanTonightResult.class);
            return Optional.ofNullable(response).filter(value -> value.result() != null
                    && value.result().recommendedBedAt() != null && value.result().recommendedWakeAt() != null
                    && value.result().reasons() != null && value.result().warnings() != null
                    && value.modelVersion() != null && !value.modelVersion().isBlank());
        } catch (ApiException error) {
            return Optional.empty();
        }
    }

    public JsonNode examSleep(JsonNode request) {
        return calculate(longRunningClient, "/ai/v1/plan/exam-sleep", request);
    }

    public JsonNode planTonight(JsonNode request) {
        return calculate(restClient, PLAN_TONIGHT_PATH, request);
    }

    public JsonNode sleepDebt(JsonNode request) {
        return calculate(restClient, "/ai/v1/sleep-debt/calculate", request);
    }

    public JsonNode scheduleRisks(JsonNode request) {
        return calculate(restClient, "/ai/v1/insights/risks", request);
    }

    public JsonNode focusSleep(JsonNode request) {
        return calculate(longRunningClient, "/ai/v1/plan/focus-sleep", request);
    }

    private JsonNode calculate(RestClient client, String path, JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        JsonNode response = post(client, path, request, JsonNode.class);
        if (response == null || !response.path("result").isObject()
                || !response.path("modelVersion").isString() || response.path("modelVersion").asText().isBlank()) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        return response;
    }

    private <T> T post(RestClient client, String path, Object request, Class<T> responseType) {
        requireServer();
        if (properties.internalKey() == null || properties.internalKey().isBlank()) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        try {
            return client.post().uri(path)
                    .header("X-Internal-Key", properties.internalKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException error) {
            throw responseError(path, error);
        } catch (RestClientException error) {
            throw unavailable(path, error);
        }
    }

    private ApiException responseError(String path, RestClientResponseException error) {
        int status = error.getStatusCode().value();
        log.warn("AI {} returned HTTP {}", path, status);
        if (status == 400 || status == 410 || status == 413 || status == 415) {
            return new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (status == 422) {
            return new ApiException(ErrorCode.AI_REQUEST_UNPROCESSABLE);
        }
        if (status == 429) {
            return new ApiException(ErrorCode.RATE_LIMITED);
        }
        return new ApiException(ErrorCode.AI_UNAVAILABLE);
    }

    private void requireServer() {
        if (!properties.isConfigured()) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private ApiException unavailable(String path, RestClientException error) {
        log.warn("AI {} request failed ({})", path, error.getClass().getSimpleName());
        return new ApiException(ErrorCode.AI_UNAVAILABLE);
    }
}
