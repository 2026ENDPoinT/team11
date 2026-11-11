package com.team11.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class SleepLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String signupAndLogin(String loginId, boolean healthDataConsent) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s", "password": "P@ssw0rd1", "nickname": "%s",
                                  "consents": [
                                    { "type": "TERMS", "agreed": true },
                                    { "type": "PRIVACY", "agreed": true },
                                    { "type": "HEALTH_DATA", "agreed": %s }
                                  ]
                                }
                                """.formatted(loginId, loginId.substring(0, Math.min(12, loginId.length())), healthDataConsent)))
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
    void start_withoutHealthDataConsent_returnsConsentRequired() throws Exception {
        String token = signupAndLogin("sleepnoconsent1", false);

        mockMvc.perform(post("/api/v1/sleep-logs/start").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void start_end_open_list_get_update_delete_fullFlow() throws Exception {
        String token = signupAndLogin("sleepflow1", true);

        mockMvc.perform(get("/api/v1/sleep-logs/open").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        String startResponse = mockMvc.perform(post("/api/v1/sleep-logs/start").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "at": "2026-11-08T23:40:00+09:00" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.source").value("TAP"))
                .andReturn().getResponse().getContentAsString();
        Integer sleepLogId = com.jayway.jsonpath.JsonPath.read(startResponse, "$.data.sleepLogId");

        // 이미 열린 기록이 있으면 다시 시작 못 함
        mockMvc.perform(post("/api/v1/sleep-logs/start").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLEEP_LOG_ALREADY_OPEN"));

        mockMvc.perform(get("/api/v1/sleep-logs/open").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sleepLogId").value(sleepLogId));

        mockMvc.perform(post("/api/v1/sleep-logs/end").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "at": "2026-11-09T07:05:00+09:00", "quality": 3 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sleepLog.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.sleepLog.date").value("2026-11-09"))
                .andExpect(jsonPath("$.data.sleepLog.durationMin").value(445))
                .andExpect(jsonPath("$.data.goalMet").value(false))
                .andExpect(jsonPath("$.data.streak").value(nullValue()));

        // 종료할 열린 기록이 없으면 409
        mockMvc.perform(post("/api/v1/sleep-logs/end").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLEEP_LOG_NOT_OPEN"));

        mockMvc.perform(get("/api/v1/sleep-logs").param("from", "2026-11-01").param("to", "2026-11-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logs", hasSize(1)))
                .andExpect(jsonPath("$.data.summary.count").value(1))
                .andExpect(jsonPath("$.data.summary.avgDurationMin").value(445));

        mockMvc.perform(get("/api/v1/sleep-logs/" + sleepLogId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quality").value(3));

        mockMvc.perform(patch("/api/v1/sleep-logs/" + sleepLogId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quality": 4 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quality").value(4));

        mockMvc.perform(delete("/api/v1/sleep-logs/" + sleepLogId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/sleep-logs/" + sleepLogId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SLEEP_LOG_NOT_FOUND"));
    }

    @Test
    void create_overlappingClosedLog_returnsOverlap() throws Exception {
        String token = signupAndLogin("sleepoverlap1", true);

        mockMvc.perform(post("/api/v1/sleep-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bedAt": "2026-10-01T01:10:00+09:00", "wakeAt": "2026-10-01T08:20:00+09:00", "quality": 4 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("MANUAL"));

        // 기존 CLOSED 구간과 겹치는 시작 시도
        mockMvc.perform(post("/api/v1/sleep-logs/start").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "at": "2026-10-01T03:00:00+09:00" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLEEP_LOG_OVERLAP"));
    }
}
