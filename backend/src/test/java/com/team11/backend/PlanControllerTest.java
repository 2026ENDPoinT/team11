package com.team11.backend;

import static com.team11.backend.ApiTestSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team11.backend.common.Kst;
import com.team11.backend.plans.service.PlanService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 테스트 설정은 AI 미설정 → NORMAL은 규칙 폴백, FOCUS/EXAM은 503. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void normal_ruleFallback_persistsAndUsesFirstEvent() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "plannormal1");
        LocalDate target = PlanService.targetDate(Kst.now());

        String first = mockMvc.perform(get("/api/v1/plans/today").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("NORMAL"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.generatedBy").value("RULE"))
                .andExpect(jsonPath("$.data.date").value(target.toString()))
                .andExpect(jsonPath("$.data.wakeAt").value(target + "T09:00:00+09:00")) // 일정 없음 → 09:00
                .andExpect(jsonPath("$.data.basisText").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        int planId = JsonPath.read(first, "$.data.planId");

        // 같은 날 다시 GET → 저장된 것 그대로
        mockMvc.perform(get("/api/v1/plans/today").with(bearer(token)).param("mode", "NORMAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value(planId));

        // 기상일 10:00 일정 추가 후 재생성 → 기상 = 10:00 − (통학40+준비50+여유10) = 08:20, 취침 = 08:20 − (465+15) = 00:20
        mockMvc.perform(post("/api/v1/events").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content("""
                        { "title": "팀플 회의", "type": "PERSONAL", "startAt": "%sT10:00:00+09:00" }
                        """.formatted(target)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/plans/today").with(bearer(token)).param("mode", "NORMAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value(planId)) // 같은 행 덮어쓰기
                .andExpect(jsonPath("$.data.basisText").value("10:00 팀플 회의"))
                .andExpect(jsonPath("$.data.wakeAt").value(target + "T08:20:00+09:00"))
                .andExpect(jsonPath("$.data.bedAt").value(target + "T00:20:00+09:00"))
                .andExpect(jsonPath("$.data.sleepMin").value(465))
                .andExpect(jsonPath("$.data.reasons.length()").value(2));
    }

    @Test
    void focus_requiresThreeLogs_thenAiUnavailableWithoutServer() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "planfocus01");
        mockMvc.perform(get("/api/v1/plans/today").with(bearer(token)).param("mode", "FOCUS"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("SLEEP_HISTORY_INSUFFICIENT"))
                .andExpect(jsonPath("$.error.details.actual").value(0));

        LocalDate today = Kst.today();
        for (int i = 3; i >= 1; i--) {
            LocalDate wakeDate = today.minusDays(i);
            mockMvc.perform(post("/api/v1/sleep-logs").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content("""
                    { "bedAt": "%sT23:00:00+09:00", "wakeAt": "%sT07:00:00+09:00", "quality": 2 }
                    """.formatted(wakeDate.minusDays(1), wakeDate))).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/v1/plans/today").with(bearer(token)).param("mode", "FOCUS"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
    }

    @Test
    void exam_requiresStudySelection_thenAiUnavailableWithoutServer() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "planexam001");
        mockMvc.perform(get("/api/v1/plans/today").with(bearer(token)).param("mode", "EXAM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("preparationState"));

        mockMvc.perform(post("/api/v1/plans/today").with(bearer(token)).param("mode", "EXAM")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "preparationState": "SEEN_NOT_RECALLABLE", "examNature": "RECALL" }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));

        // eventId를 보냈는데 시험이 아니면 EVENT_NOT_FOUND
        String personal = mockMvc.perform(post("/api/v1/events").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content("""
                        { "title": "약속", "type": "PERSONAL", "startAt": "2026-12-01T19:00:00+09:00" }
                        """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int eventId = JsonPath.read(personal, "$.data.eventId");
        mockMvc.perform(post("/api/v1/plans/today").with(bearer(token)).param("mode", "EXAM")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "eventId": %d, "preparationState": "FINAL_REVIEW", "examNature": "MIXED" }
                                """.formatted(eventId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void invalidMode_returnsValidationFailed() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "planmode001");
        mockMvc.perform(get("/api/v1/plans/today").with(bearer(token)).param("mode", "PARTY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
