package com.team11.backend;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StreakControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String signupAndLogin(String loginId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s", "password": "P@ssw0rd1", "nickname": "%s",
                                  "consents": [
                                    { "type": "TERMS", "agreed": true },
                                    { "type": "PRIVACY", "agreed": true },
                                    { "type": "HEALTH_DATA", "agreed": true }
                                  ]
                                }
                                """.formatted(loginId, loginId.substring(0, Math.min(12, loginId.length())))))
                .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "loginId": "%s", "password": "P@ssw0rd1" }
                                """.formatted(loginId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(loginResponse, "$.data.accessToken");
    }

    @Test
    void streak_newUser_isAllZero() throws Exception {
        String token = signupAndLogin("streaknew1");

        mockMvc.perform(get("/api/v1/streak").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentCount").value(0))
                .andExpect(jsonPath("$.data.bestCount").value(0))
                .andExpect(jsonPath("$.data.lastAchievedDate").value(nullValue()))
                .andExpect(jsonPath("$.data.todayAchieved").value(false));
    }

    @Test
    void dailyResults_withNoRecordAndNoPlan_distinguishesMissingVsPending() throws Exception {
        String token = signupAndLogin("streakdaily1");

        // 과거 특정 날짜에 직접 입력으로 수면 기록 하나 생성 (그 날짜엔 Plan이 생성된 적 없음)
        mockMvc.perform(post("/api/v1/sleep-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bedAt": "2026-01-09T23:30:00+09:00", "wakeAt": "2026-01-10T07:30:00+09:00", "quality": 4 }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/daily-results").param("from", "2026-01-09").param("to", "2026-01-11")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", org.hamcrest.Matchers.hasSize(3)))
                // 2026-01-09: 기록 없음(기상일이 아님) → 미달성, 실제 취침시각 없음
                .andExpect(jsonPath("$.data.results[0].date").value("2026-01-09"))
                .andExpect(jsonPath("$.data.results[0].goalMet").value(false))
                .andExpect(jsonPath("$.data.results[0].actualBedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.results[0].finalized").value(true))
                // 2026-01-10: 기록은 있지만 그 날짜 Plan이 없어 판정 보류 → goalMet false, recommendedBedAt/deviation null인데도 actualBedAt은 채워짐
                .andExpect(jsonPath("$.data.results[1].date").value("2026-01-10"))
                .andExpect(jsonPath("$.data.results[1].goalMet").value(false))
                .andExpect(jsonPath("$.data.results[1].recommendedBedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.results[1].deviationMin").value(nullValue()))
                .andExpect(jsonPath("$.data.results[1].actualBedAt").value(org.hamcrest.Matchers.containsString("2026-01-09T23:30:00")))
                .andExpect(jsonPath("$.data.results[1].durationMin").value(480))
                .andExpect(jsonPath("$.data.results[1].finalized").value(true))
                // 2026-01-11: 기록 없음
                .andExpect(jsonPath("$.data.results[2].date").value("2026-01-11"))
                .andExpect(jsonPath("$.data.results[2].goalMet").value(false));
    }

    @Test
    void dailyResults_withFromAfterTo_returnsValidationFailed() throws Exception {
        String token = signupAndLogin("streakbadrange1");

        mockMvc.perform(get("/api/v1/daily-results").param("from", "2026-01-10").param("to", "2026-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void dailyResults_withRangeOver93Days_returnsDateRangeTooLarge() throws Exception {
        String token = signupAndLogin("streaktoolong1");

        mockMvc.perform(get("/api/v1/daily-results").param("from", "2026-01-01").param("to", "2026-05-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DATE_RANGE_TOO_LARGE"));
    }
}
