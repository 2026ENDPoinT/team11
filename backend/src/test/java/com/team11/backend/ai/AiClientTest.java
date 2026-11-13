package com.team11.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import com.team11.backend.common.ErrorCode;
import com.team11.backend.common.exception.ApiException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AiClientTest {

    @Test
    void forwardsAi01Through06WithCorrectMethodsAndFixedLengthJson() throws IOException {
        var received = new ConcurrentLinkedQueue<Received>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/v1/", exchange -> {
            try (exchange) {
                var headers = exchange.getRequestHeaders();
                received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        headers.getFirst("X-Internal-Key"), headers.getFirst("Content-Length"),
                        headers.getFirst("Transfer-Encoding"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                String reply = switch (exchange.getRequestURI().getPath()) {
                    case "/ai/v1/health" -> "{\"status\":\"ok\"}";
                    default -> "{\"result\":{\"status\":\"READY\"},\"modelVersion\":\"test\"}";
                };
                byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var client = new AiClient(new AiProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-key", 4, 15), RestClient.builder());
            var mapper = JsonMapper.builder().build();
            var request = mapper.readTree("{\"title\":\"시험\"}");

            assertEquals("ok", client.health().path("status").asText());
            assertEquals(new Received("GET", "/ai/v1/health", null, null, null, ""), received.poll());

            var endpoints = List.of(
                    new Endpoint("/ai/v1/plan/tonight", client::planTonight),
                    new Endpoint("/ai/v1/plan/exam-sleep", client::examSleep),
                    new Endpoint("/ai/v1/sleep-debt/calculate", client::sleepDebt),
                    new Endpoint("/ai/v1/insights/risks", client::scheduleRisks),
                    new Endpoint("/ai/v1/plan/focus-sleep", client::focusSleep));
            for (Endpoint endpoint : endpoints) {
                assertEquals("test", endpoint.call().apply(request).path("modelVersion").asText());
                Received call = received.poll();
                assertNotNull(call);
                assertEquals("POST", call.method());
                assertEquals(endpoint.path(), call.path());
                assertEquals("test-key", call.key());
                assertEquals(Integer.toString(call.body().getBytes(StandardCharsets.UTF_8).length), call.length());
                assertNull(call.transferEncoding());
                assertEquals(request, mapper.readTree(call.body()));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingConfigurationReturnsServiceUnavailable() {
        var client = new AiClient(new AiProperties("", "", 4, 15), RestClient.builder());
        assertEquals(ErrorCode.AI_UNAVAILABLE, assertThrows(ApiException.class, client::health).getErrorCode());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SLEEP_AI_TEST_EXAMPLES", matches = ".+")
    void callsRunningAiServerWithItsContractExamples() {
        var client = new AiClient(new AiProperties(System.getenv("AI_BASE_URL"),
                System.getenv("AI_INTERNAL_KEY"), 4, 15), RestClient.builder());
        var mapper = JsonMapper.builder().build();
        Path examples = Path.of(System.getenv("SLEEP_AI_TEST_EXAMPLES"));

        assertEquals("ok", client.health().path("status").asText());
        var examplesByEndpoint = List.of(
                new Endpoint("ai02", client::planTonight),
                new Endpoint("ai03-spring-snapshot", client::examSleep),
                new Endpoint("ai04", client::sleepDebt),
                new Endpoint("ai05", client::scheduleRisks),
                new Endpoint("ai06-focus", client::focusSleep));
        for (Endpoint endpoint : examplesByEndpoint) {
            var request = mapper.readTree(examples.resolve(endpoint.path() + "-request.json").toFile());
            var expected = mapper.readTree(examples.resolve(endpoint.path() + "-response.json").toFile());
            assertEquals(expected.path("modelVersion").asText(),
                    endpoint.call().apply(request).path("modelVersion").asText(), endpoint.path());
        }
    }

    private record Endpoint(String path, Function<JsonNode, JsonNode> call) {}

    private record Received(String method, String path, String key, String length, String transferEncoding,
                            String body) {}
}
