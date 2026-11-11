package com.team11.backend;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
class StatsControllerTest {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

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

    private void createSleepLog(String token, LocalDate date, LocalTime bed, LocalTime wake) throws Exception {
        OffsetDateTime bedAt = date.minusDays(1).atTime(bed).atZone(ASIA_SEOUL).toOffsetDateTime();
        OffsetDateTime wakeAt = date.atTime(wake).atZone(ASIA_SEOUL).toOffsetDateTime();
        mockMvc.perform(post("/api/v1/sleep-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bedAt": "%s", "wakeAt": "%s", "quality": 4 }
                                """.formatted(bedAt, wakeAt)))
                .andExpect(status().isCreated());
    }

    @Test
    void sleepDebt_withTwoDaysOfRecords_computesCumulativeDebt() throws Exception {
        String token = signupAndLogin("statsdebt1");
        LocalDate today = LocalDate.now(ASIA_SEOUL);

        // 465분 필요, 400분/430분 자서 둘 다 부족
        createSleepLog(token, today.minusDays(1), LocalTime.of(23, 0), LocalTime.of(5, 40));
        createSleepLog(token, today, LocalTime.of(23, 0), LocalTime.of(6, 10));

        mockMvc.perform(get("/api/v1/sleep-debt").param("range", "7d").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insufficientData").value(false))
                .andExpect(jsonPath("$.data.sleepNeedMin").value(465))
                .andExpect(jsonPath("$.data.currentDebtMin").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void sleepDebt_withInvalidRange_returnsValidationFailed() throws Exception {
        String token = signupAndLogin("statsdebt2");

        mockMvc.perform(get("/api/v1/sleep-debt").param("range", "30d").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void sleepDebt_withFewerThanTwoRecords_returnsInsufficientData() throws Exception {
        String token = signupAndLogin("statsdebt3");

        mockMvc.perform(get("/api/v1/sleep-debt").param("range", "7d").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insufficientData").value(true))
                .andExpect(jsonPath("$.data.daily", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void weekly_withNonMondayWeekStart_returnsValidationFailed() throws Exception {
        String token = signupAndLogin("statsweekly1");

        mockMvc.perform(get("/api/v1/stats/weekly").param("weekStart", "2026-11-03")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void weekly_withNoRecords_returnsInsufficientData() throws Exception {
        String token = signupAndLogin("statsweekly2");

        mockMvc.perform(get("/api/v1/stats/weekly").param("weekStart", "2026-11-02")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insufficientData").value(true))
                .andExpect(jsonPath("$.data.vsLastWeek").value(nullValue()));
    }

    @Test
    void trend_withInvalidMetric_returnsValidationFailed() throws Exception {
        String token = signupAndLogin("statstrend1");

        mockMvc.perform(get("/api/v1/stats/trend").param("range", "30d").param("metric", "unknown")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void trend_withSleepMinMetric_returnsRecordedPoints() throws Exception {
        String token = signupAndLogin("statstrend2");
        LocalDate today = LocalDate.now(ASIA_SEOUL);
        createSleepLog(token, today, LocalTime.of(23, 0), LocalTime.of(7, 0));

        mockMvc.perform(get("/api/v1/stats/trend").param("range", "7d").param("metric", "sleepMin")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.points[0].value").value(480));
    }

    @Test
    void upcomingRisks_withDrinkingEvent_returnsDrinkingRisk() throws Exception {
        String token = signupAndLogin("statsrisk1");
        LocalDate target = LocalDate.now(ASIA_SEOUL).plusDays(2);

        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "PERSONAL", "title": "동기 술자리", "startAt": "%sT19:00:00+09:00",
                                  "endAt": "%sT23:40:00+09:00", "tag": "DRINKING" }
                                """.formatted(target.minusDays(1), target.minusDays(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/insights/upcoming-risks").param("days", "3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.risks[0].type").value("DRINKING"))
                .andExpect(jsonPath("$.data.risks[0].severity").value("HIGH"));
    }

    @Test
    void upcomingRisks_withDaysOutOfRange_returnsValidationFailed() throws Exception {
        String token = signupAndLogin("statsrisk2");

        mockMvc.perform(get("/api/v1/insights/upcoming-risks").param("days", "8")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void chronotype_withFewerThanSevenRecords_returnsInsufficientDataAndCurrentSetting() throws Exception {
        String token = signupAndLogin("statschrono1");

        mockMvc.perform(get("/api/v1/insights/chronotype").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insufficientData").value(true))
                .andExpect(jsonPath("$.data.chronotype").value("NEUTRAL"))
                .andExpect(jsonPath("$.data.applied").value(true));
    }
}
