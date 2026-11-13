package com.team11.backend;

import static com.team11.backend.ApiTestSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
    MockMvc mockMvc;

    @Test
    void create_duplicateDate_update_list_delete() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "sleeplog001");

        String created = mockMvc.perform(post("/api/v1/sleep-logs").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "bedAt": "2026-10-05T23:30:00+09:00", "wakeAt": "2026-10-06T07:00:00+09:00",
                                  "quality": 2, "memo": "조금 뒤척임" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.date").value("2026-10-06"))
                .andExpect(jsonPath("$.data.durationMin").value(450))
                .andExpect(jsonPath("$.data.bedAt").value("2026-10-05T23:30:00+09:00"))
                .andReturn().getResponse().getContentAsString();
        int logId = JsonPath.read(created, "$.data.sleepLogId");

        // 같은 기상일에 또 기록
        mockMvc.perform(post("/api/v1/sleep-logs").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "bedAt": "2026-10-06T01:00:00+09:00", "wakeAt": "2026-10-06T08:00:00+09:00" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLEEP_LOG_DUPLICATE_DATE"))
                .andExpect(jsonPath("$.error.details.sleepLogId").value(logId));

        mockMvc.perform(post("/api/v1/sleep-logs").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "bedAt": "2026-10-08T07:00:00+09:00", "wakeAt": "2026-10-08T06:00:00+09:00" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TIME_RANGE_INVALID"));

        mockMvc.perform(post("/api/v1/sleep-logs").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "bedAt": "2026-10-08T00:00:00+09:00", "wakeAt": "2026-10-08T07:00:00+09:00", "quality": 4 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/v1/sleep-logs/" + logId).with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "bedAt": "2026-10-05T23:00:00+09:00", "wakeAt": "2026-10-06T07:30:00+09:00", "quality": 3 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMin").value(510))
                .andExpect(jsonPath("$.data.quality").value(3))
                .andExpect(jsonPath("$.data.memo").doesNotExist());

        mockMvc.perform(get("/api/v1/sleep-logs").with(bearer(token)).param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logs.length()").value(1))
                .andExpect(jsonPath("$.data.logs[0].wakeAt").value("2026-10-06T07:30:00+09:00"));

        mockMvc.perform(delete("/api/v1/sleep-logs/" + logId).with(bearer(token))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sleep-logs/" + logId).with(bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SLEEP_LOG_NOT_FOUND"));
    }
}
