package com.team11.backend;

import static com.team11.backend.ApiTestSupport.bearer;
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
class ReportControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void weeklyReport_aggregatesLogsInWeek() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "report00001");
        // 2026-11-02(월)~08(일) 주. 기상일 11-03(480분, 취침 23:00), 11-04(420분, 취침 00:00)
        mockMvc.perform(post("/api/v1/sleep-logs").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content("""
                { "bedAt": "2026-11-02T23:00:00+09:00", "wakeAt": "2026-11-03T07:00:00+09:00", "quality": 3 }
                """)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/sleep-logs").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content("""
                { "bedAt": "2026-11-04T00:00:00+09:00", "wakeAt": "2026-11-04T07:00:00+09:00", "quality": 1 }
                """)).andExpect(status().isCreated());
        // 다른 주 기록은 제외
        mockMvc.perform(post("/api/v1/sleep-logs").with(bearer(token)).contentType(MediaType.APPLICATION_JSON).content("""
                { "bedAt": "2026-11-10T01:00:00+09:00", "wakeAt": "2026-11-10T05:00:00+09:00" }
                """)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/reports").with(bearer(token)).param("period", "WEEK").param("date", "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-11-02"))
                .andExpect(jsonPath("$.data.to").value("2026-11-08"))
                .andExpect(jsonPath("$.data.loggedDays").value(2))
                .andExpect(jsonPath("$.data.avgSleepMin").value(450))
                .andExpect(jsonPath("$.data.avgBedTime").value("23:30"))
                .andExpect(jsonPath("$.data.regularityPct").value(50)) // 중앙값 23:30, 평균편차 30분
                .andExpect(jsonPath("$.data.daily.length()").value(7))
                .andExpect(jsonPath("$.data.daily[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.daily[0].sleepMin").doesNotExist())
                .andExpect(jsonPath("$.data.daily[1].sleepMin").value(480))
                .andExpect(jsonPath("$.data.daily[2].quality").value(1));

        mockMvc.perform(get("/api/v1/reports").with(bearer(token)).param("period", "MONTH").param("date", "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-11-01"))
                .andExpect(jsonPath("$.data.to").value("2026-11-30"))
                .andExpect(jsonPath("$.data.loggedDays").value(3))
                .andExpect(jsonPath("$.data.daily.length()").value(30));
    }

    @Test
    void emptyReport_hasNullMetrics() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "reportempty");
        mockMvc.perform(get("/api/v1/reports").with(bearer(token)).param("date", "2026-03-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("WEEK"))
                .andExpect(jsonPath("$.data.loggedDays").value(0))
                .andExpect(jsonPath("$.data.avgSleepMin").doesNotExist())
                .andExpect(jsonPath("$.data.regularityPct").doesNotExist());
    }
}
