package com.team11.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import com.team11.backend.ai.dto.AiFocusSleepRequest;
import com.team11.backend.ai.dto.AiFocusSleepResult;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 실제 AI 서버 특성(단일 Content-Length 요구, 응답 octet-stream, 422 본문)을 JDK HttpServer로 흉내 낸다. */
class AiClientTest {

    private static final AiFocusSleepRequest REQUEST = new AiFocusSleepRequest("FOCUS_SLEEP_V1",
            OffsetDateTime.parse("2026-11-14T23:00:00+09:00"), "Asia/Seoul", OffsetDateTime.parse("2026-11-15T07:00:00+09:00"),
            360, 480, List.of(new AiFocusSleepRequest.History(LocalDate.parse("2026-11-13"), "23:00", "07:00", 420, 30)));

    private AiClient client(HttpServer server) {
        var props = new AiProperties("http://127.0.0.1:" + server.getAddress().getPort(), "k",
                new AiProperties.Timeout(4, 12, 12));
        return new AiClient(props, RestClient.builder(), JsonMapper.builder().build());
    }

    private HttpServer server(AtomicReference<String> seenLength, int status, String body) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/", ex -> {
            seenLength.set(ex.getRequestHeaders().getFirst("Content-Length"));
            byte[] req = ex.getRequestBody().readAllBytes();
            boolean ok = seenLength.get() != null && Integer.parseInt(seenLength.get()) == req.length;
            byte[] out = (ok ? body : "{\"error\":{\"code\":\"INVALID_INPUT\"}}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/octet-stream");
            ex.sendResponseHeaders(ok ? status : 400, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        s.start();
        return s;
    }

    @Test
    void sendsContentLength_andParsesOctetStreamResponse() throws Exception {
        AtomicReference<String> len = new AtomicReference<>();
        HttpServer s = server(len, 200, """
                {"modelVersion":"v","result":{"recommendation":{"bedtime":"2026-11-14T23:30:00+09:00",
                "wake_time":"2026-11-15T07:00:00+09:00","sleep_opportunity_hours":7.5,"estimated_actual_sleep_hours":7.0}}}
                """);
        try {
            AiFocusSleepResult r = client(s).focusSleepPlan(REQUEST);
            assertNotNull(len.get());
            assertNotNull(r.result().recommendation());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void unprocessable_isRejected_evenWithOctetStream() throws Exception {
        HttpServer s = server(new AtomicReference<>(), 422, "{\"error\":{\"code\":\"NO_FEASIBLE_PLAN\",\"message\":\"x\"}}");
        try {
            AiRejectedException e = assertThrows(AiRejectedException.class, () -> client(s).focusSleepPlan(REQUEST));
            assertEquals("NO_FEASIBLE_PLAN", e.getCode());
        } finally {
            s.stop(0);
        }
    }
}
