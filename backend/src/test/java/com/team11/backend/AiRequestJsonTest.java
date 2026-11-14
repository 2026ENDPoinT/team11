package com.team11.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.team11.backend.ai.dto.AiFocusSleepRequest;
import com.team11.backend.common.Kst;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

/** AI 서버로 나가는 시각은 소수점 없는 "yyyy-MM-ddTHH:mm:ss+09:00"여야 한다(실제 Boot ObjectMapper로 확인). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AiRequestJsonTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void asOfAt_hasKstOffsetAndNoFraction() {
        var req = new AiFocusSleepRequest("FOCUS_SLEEP_V1", Kst.now(), "Asia/Seoul", Kst.now().plusHours(8), 360, 480, List.of());
        String json = objectMapper.writeValueAsString(req);
        assertTrue(json.matches(".*\"asOfAt\":\"\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\+09:00\".*"), json);
    }
}
