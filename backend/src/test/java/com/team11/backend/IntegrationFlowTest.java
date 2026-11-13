package com.team11.backend;

import static com.team11.backend.ApiTestSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team11.backend.common.Kst;
import com.team11.backend.plans.service.PlanService;
import com.team11.backend.timetable.config.SemesterProperties;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 핵심 흐름: 가입 → 로그인 → 온보딩 → 시간표 → 일정 → 오늘 플랜(규칙 폴백) → 수면기록 → 주간 리포트 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class IntegrationFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SemesterProperties semesterProperties;

    @Test
    void signupToReport() throws Exception {
        String loginId = "flowuser001";
        ApiTestSupport.signup(mockMvc, loginId);
        String login = ApiTestSupport.login(mockMvc, loginId);
        String token = JsonPath.read(login, "$.data.accessToken");

        mockMvc.perform(post("/api/v1/users/me/onboarding").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(ApiTestSupport.ONBOARDING_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingDone").value(true));

        LocalDate target = PlanService.targetDate(Kst.now());
        String dow = com.team11.backend.timetable.domain.DayOfWeek.of(target.getDayOfWeek()).name();
        boolean inSemester = semesterProperties.containing(target).isPresent();
        String semester = semesterProperties.resolveFor(target).map(c -> c.code()).orElse("2026-2");

        // 기상일 요일 09:00 수업
        mockMvc.perform(post("/api/v1/timetable/entries").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "semester": "%s", "dayOfWeek": "%s", "startTime": "09:00", "endTime": "10:15",
                                  "courseName": "오픈소스소프트웨어", "room": "미래관-319" }
                                """.formatted(semester, dow)))
                .andExpect(status().isCreated());

        // 기상일 11:00 일정(수업보다 늦어 첫 일정은 수업이어야 함)
        mockMvc.perform(post("/api/v1/events").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "title": "과제 제출", "type": "ASSIGNMENT", "startAt": "%sT11:00:00+09:00" }
                                """.formatted(target)))
                .andExpect(status().isCreated());

        String expectedBasis = inSemester ? "09:00 오픈소스소프트웨어" : "11:00 과제 제출";
        String expectedWake = inSemester ? target + "T07:20:00+09:00" : target + "T09:20:00+09:00";
        mockMvc.perform(get("/api/v1/plans/today").with(bearer(token)).param("mode", "NORMAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedBy").value("RULE"))
                .andExpect(jsonPath("$.data.basisText").value(expectedBasis))
                .andExpect(jsonPath("$.data.wakeAt").value(expectedWake));

        LocalDate wakeDate = Kst.today();
        mockMvc.perform(post("/api/v1/sleep-logs").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "bedAt": "%sT23:30:00+09:00", "wakeAt": "%sT07:00:00+09:00", "quality": 2 }
                                """.formatted(wakeDate.minusDays(1), wakeDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.durationMin").value(450));

        mockMvc.perform(get("/api/v1/reports").with(bearer(token)).param("period", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loggedDays").value(1))
                .andExpect(jsonPath("$.data.avgSleepMin").value(450))
                .andExpect(jsonPath("$.data.avgBedTime").value("23:30"))
                .andExpect(jsonPath("$.data.daily.length()").value(7));
    }
}
