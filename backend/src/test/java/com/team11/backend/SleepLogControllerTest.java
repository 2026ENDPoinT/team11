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

    @Test
    void sync_wearableSleepLogs_createsThenResyncsWithStagesAndSkipsOverlap() throws Exception {
        String token = signupAndLogin("sleepwearable1", true);

        String syncResponse = mockMvc.perform(post("/api/v1/sleep-logs/sync").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "logs": [ {
                                  "bedAt": "2026-10-02T23:30:00+09:00", "wakeAt": "2026-10-03T07:00:00+09:00", "quality": 3,
                                  "stages": [
                                    { "stage": "AWAKE", "startAt": "2026-10-02T23:30:00+09:00", "endAt": "2026-10-02T23:40:00+09:00" },
                                    { "stage": "DEEP",  "startAt": "2026-10-02T23:40:00+09:00", "endAt": "2026-10-03T01:00:00+09:00" },
                                    { "stage": "REM",   "startAt": "2026-10-03T01:00:00+09:00", "endAt": "2026-10-03T07:00:00+09:00" }
                                  ]
                                } ] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncedCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andExpect(jsonPath("$.data.logs[0].source").value("WEARABLE"))
                .andExpect(jsonPath("$.data.logs[0].stages", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        Integer sleepLogId = com.jayway.jsonpath.JsonPath.read(syncResponse, "$.data.logs[0].sleepLogId");

        // 같은 날짜 재동기화 -> 새로 만들지 않고 기존 WEARABLE 기록/단계를 갱신
        mockMvc.perform(post("/api/v1/sleep-logs/sync").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "logs": [ {
                                  "bedAt": "2026-10-02T23:30:00+09:00", "wakeAt": "2026-10-03T07:00:00+09:00", "quality": 5,
                                  "stages": [
                                    { "stage": "LIGHT", "startAt": "2026-10-02T23:30:00+09:00", "endAt": "2026-10-03T07:00:00+09:00" }
                                  ]
                                } ] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncedCount").value(1))
                .andExpect(jsonPath("$.data.logs[0].sleepLogId").value(sleepLogId))
                .andExpect(jsonPath("$.data.logs[0].quality").value(5))
                .andExpect(jsonPath("$.data.logs[0].stages", hasSize(1)));

        mockMvc.perform(get("/api/v1/sleep-logs/" + sleepLogId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stages", hasSize(1)))
                .andExpect(jsonPath("$.data.stages[0].stage").value("LIGHT"));

        // 기존 수동 기록과 겹치는 웨어러블 항목은 건너뛴다
        mockMvc.perform(post("/api/v1/sleep-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bedAt": "2026-10-10T00:00:00+09:00", "wakeAt": "2026-10-10T08:00:00+09:00" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/sleep-logs/sync").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "logs": [ {
                                  "bedAt": "2026-10-10T01:00:00+09:00", "wakeAt": "2026-10-10T07:00:00+09:00"
                                } ] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncedCount").value(0))
                .andExpect(jsonPath("$.data.skippedCount").value(1));
    }
}
