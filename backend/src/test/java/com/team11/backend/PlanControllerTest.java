package com.team11.backend;

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
class PlanControllerTest {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    private LocalDate tonightTargetDate() {
        OffsetDateTime now = OffsetDateTime.now(ASIA_SEOUL);
        return now.toLocalTime().isBefore(LocalTime.of(6, 0)) ? now.toLocalDate() : now.toLocalDate().plusDays(1);
    }

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
    void tonightPlan_withoutHealthDataConsent_returnsConsentRequired() throws Exception {
        String token = signupAndLogin("plannoconsent1", false);

        mockMvc.perform(get("/api/v1/plans/tonight").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void tonightPlan_withNoSchedule_isFreeModeAndIdempotent() throws Exception {
        String token = signupAndLogin("planfree1", true);

        String first = mockMvc.perform(get("/api/v1/plans/tonight").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("FREE"))
                .andExpect(jsonPath("$.data.recommendedWakeAt").value(org.hamcrest.Matchers.containsString("09:00:00")))
                .andExpect(jsonPath("$.data.recommendedBedAt").value(org.hamcrest.Matchers.containsString("01:00:00")))
                .andExpect(jsonPath("$.data.generatedBy").value("RULE"))
                .andReturn().getResponse().getContentAsString();
        Integer firstPlanId = com.jayway.jsonpath.JsonPath.read(first, "$.data.planId");

        // 같은 날 다시 호출하면 같은 플랜(get-or-create)
        mockMvc.perform(get("/api/v1/plans/tonight").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value(firstPlanId));
    }

    @Test
    void tonightPlan_withClassTomorrow_isNormalModeWithComputedTimes() throws Exception {
        String token = signupAndLogin("plannormal1", true);
        LocalDate targetDate = tonightTargetDate();
        String dayOfWeek = targetDate.getDayOfWeek().name().substring(0, 3);

        mockMvc.perform(post("/api/v1/timetable/entries").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "semester": "2026-2", "dayOfWeek": "%s", "startTime": "09:00", "endTime": "10:15",
                                  "courseName": "자료구조" }
                                """.formatted(dayOfWeek)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/plans/tonight").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("NORMAL"))
                .andExpect(jsonPath("$.data.firstEvent.kind").value("CLASS"))
                .andExpect(jsonPath("$.data.firstEvent.title").value("자료구조"))
                // 기본 설정: commute0+prep0+buffer10 → 기상 08:50, sleepNeed465+latency15=8h → 취침 00:50
                .andExpect(jsonPath("$.data.recommendedWakeAt").value(org.hamcrest.Matchers.containsString("08:50:00")))
                .andExpect(jsonPath("$.data.recommendedBedAt").value(org.hamcrest.Matchers.containsString("00:50:00")))
                .andExpect(jsonPath("$.data.breakdown.sleepNeedMin").value(465));

        mockMvc.perform(get("/api/v1/plans").param("date", targetDate.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("NORMAL"));
    }

    @Test
    void getPlanByDate_withoutExistingPlan_returnsPlanNotFound() throws Exception {
        String token = signupAndLogin("plannotfound1", true);

        mockMvc.perform(get("/api/v1/plans").param("date", "2099-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    void submitPlanFeedback_thenResubmit_overwritesLastValue() throws Exception {
        String token = signupAndLogin("planfeedback1", true);

        String tonight = mockMvc.perform(get("/api/v1/plans/tonight").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer planId = com.jayway.jsonpath.JsonPath.read(tonight, "$.data.planId");

        mockMvc.perform(post("/api/v1/plans/" + planId + "/feedback").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rating": "TOO_LATE", "followed": false, "reason": "SOCIAL" }
                                """))
                .andExpect(status().isNoContent());

        // 같은 플랜에 다시 보내면 마지막 값으로 덮어씀 (에러 없이 204)
        mockMvc.perform(post("/api/v1/plans/" + planId + "/feedback").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rating": "GOOD" }
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void submitPlanFeedback_withoutRating_returnsValidationFailed() throws Exception {
        String token = signupAndLogin("planfeedback2", true);

        String tonight = mockMvc.perform(get("/api/v1/plans/tonight").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer planId = com.jayway.jsonpath.JsonPath.read(tonight, "$.data.planId");

        mockMvc.perform(post("/api/v1/plans/" + planId + "/feedback").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void submitPlanFeedback_onOtherUsersPlan_returnsForbidden() throws Exception {
        String ownerToken = signupAndLogin("planfeedowner1", true);
        String otherToken = signupAndLogin("planfeedother1", true);

        String tonight = mockMvc.perform(get("/api/v1/plans/tonight").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer planId = com.jayway.jsonpath.JsonPath.read(tonight, "$.data.planId");

        mockMvc.perform(post("/api/v1/plans/" + planId + "/feedback").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rating": "GOOD" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void submitPlanFeedback_onMissingPlan_returnsPlanNotFound() throws Exception {
        String token = signupAndLogin("planfeedmissing1", true);

        mockMvc.perform(post("/api/v1/plans/999999/feedback").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rating": "GOOD" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PLAN_NOT_FOUND"));
    }
}
