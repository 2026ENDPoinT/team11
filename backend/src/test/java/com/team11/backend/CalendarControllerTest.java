package com.team11.backend;

import static org.hamcrest.Matchers.hasSize;
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
class CalendarControllerTest {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    private LocalDate tonightTargetDate() {
        OffsetDateTime now = OffsetDateTime.now(ASIA_SEOUL);
        return now.toLocalTime().isBefore(LocalTime.of(6, 0)) ? now.toLocalDate() : now.toLocalDate().plusDays(1);
    }

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
    void getMonth_includesClassCountEventsAndPlan() throws Exception {
        String token = signupAndLogin("cal1");
        LocalDate today = LocalDate.now(ASIA_SEOUL);
        String dayOfWeek = today.getDayOfWeek().name().substring(0, 3);

        mockMvc.perform(post("/api/v1/timetable/entries").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "semester": "2026-2", "dayOfWeek": "%s", "startTime": "09:00", "endTime": "10:15",
                                  "courseName": "자료구조" }
                                """.formatted(dayOfWeek)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "EXAM", "title": "자료구조 중간고사", "startAt": "%sT13:00:00+09:00" }
                                """.formatted(today)))
                .andExpect(status().isCreated());

        LocalDate planDate = tonightTargetDate();
        mockMvc.perform(get("/api/v1/plans/tonight").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String month = today.toString().substring(0, 7);
        String response = mockMvc.perform(get("/api/v1/calendar/month").param("month", month)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(month))
                .andReturn().getResponse().getContentAsString();

        int todayIndex = today.getDayOfMonth() - 1;
        mockMvc.perform(get("/api/v1/calendar/month").param("month", month)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.days[%d].date".formatted(todayIndex)).value(today.toString()))
                .andExpect(jsonPath("$.data.days[%d].classCount".formatted(todayIndex)).value(1))
                .andExpect(jsonPath("$.data.days[%d].firstClassTime".formatted(todayIndex)).value("09:00:00"))
                .andExpect(jsonPath("$.data.days[%d].events".formatted(todayIndex), hasSize(1)))
                .andExpect(jsonPath("$.data.days[%d].events[0].title".formatted(todayIndex)).value("자료구조 중간고사"));

        if (planDate.getMonth() == today.getMonth() && planDate.getYear() == today.getYear()) {
            int planIndex = planDate.getDayOfMonth() - 1;
            mockMvc.perform(get("/api/v1/calendar/month").param("month", month)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.data.days[%d].plan".formatted(planIndex)).exists())
                    .andExpect(jsonPath("$.data.days[%d].plan".formatted(planIndex)).value(org.hamcrest.Matchers.not(nullValue())));
        }
    }

    @Test
    void getDay_returnsClassesEventsAndEmptyConditionLogs() throws Exception {
        String token = signupAndLogin("cal2");
        LocalDate today = LocalDate.now(ASIA_SEOUL);
        String dayOfWeek = today.getDayOfWeek().name().substring(0, 3);

        mockMvc.perform(post("/api/v1/timetable/entries").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "semester": "2026-2", "dayOfWeek": "%s", "startTime": "09:00", "endTime": "10:15",
                                  "courseName": "자료구조" }
                                """.formatted(dayOfWeek)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/calendar/day").param("date", today.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(today.toString()))
                .andExpect(jsonPath("$.data.classes", hasSize(1)))
                .andExpect(jsonPath("$.data.classes[0].courseName").value("자료구조"))
                .andExpect(jsonPath("$.data.conditionLogs", hasSize(0)))
                .andExpect(jsonPath("$.data.sleepLog").value(nullValue()))
                .andExpect(jsonPath("$.data.prepLog").value(nullValue()));
    }

    @Test
    void getMonth_withInvalidFormat_returnsValidationFailed() throws Exception {
        String token = signupAndLogin("cal3");

        mockMvc.perform(get("/api/v1/calendar/month").param("month", "2026/11")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
