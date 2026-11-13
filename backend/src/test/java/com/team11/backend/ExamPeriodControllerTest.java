package com.team11.backend;

import static org.hamcrest.Matchers.hasSize;
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
class ExamPeriodControllerTest {

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
                                    { "type": "PRIVACY", "agreed": true }
                                  ]
                                }
                                """.formatted(loginId, loginId)))
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

    private Integer createExamEvent(String token, String title, String startAt) throws Exception {
        String response = mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "EXAM", "title": "%s", "startAt": "%s" }
                                """.formatted(title, startAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.data.event.eventId");
    }

    @Test
    void examPeriod_create_list_get_update_delete() throws Exception {
        String token = signupAndLogin("examuser1");
        Integer examEventId = createExamEvent(token, "자료구조 중간고사", "2026-11-12T10:00:00+09:00");

        String createResponse = mockMvc.perform(post("/api/v1/exam-periods")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "2학기 중간고사", "from": "2026-11-09", "to": "2026-11-14",
                                  "exams": [ { "eventId": %d, "difficulty": 4, "studyHoursNeeded": 10 } ],
                                  "constraints": { "dailyStudyHoursMax": 8, "minSleepMin": 300, "includeNaps": true } }
                                """.formatted(examEventId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("2학기 중간고사"))
                .andExpect(jsonPath("$.data.exams", hasSize(1)))
                .andExpect(jsonPath("$.data.exams[0].title").value("자료구조 중간고사"))
                .andExpect(jsonPath("$.data.constraints.minSleepMin").value(300))
                .andExpect(jsonPath("$.data.planSummary.generated").value(false))
                .andReturn().getResponse().getContentAsString();
        Integer examPeriodId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.examPeriodId");

        mockMvc.perform(get("/api/v1/exam-periods").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examPeriods", hasSize(1)))
                .andExpect(jsonPath("$.data.examPeriods[0].status").value("ONGOING"))
                .andExpect(jsonPath("$.data.examPeriods[0].examCount").value(1));

        mockMvc.perform(get("/api/v1/exam-periods/" + examPeriodId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("2학기 중간고사"));

        mockMvc.perform(patch("/api/v1/exam-periods/" + examPeriodId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "2학기 중간고사(수정)" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("2학기 중간고사(수정)"))
                .andExpect(jsonPath("$.data.exams", hasSize(1)));

        mockMvc.perform(delete("/api/v1/exam-periods/" + examPeriodId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/exam-periods/" + examPeriodId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXAM_PERIOD_NOT_FOUND"));
    }

    @Test
    void createExamPeriod_withoutConstraints_fallsBackToUserMinSleepMin() throws Exception {
        String token = signupAndLogin("examuser2");
        Integer examEventId = createExamEvent(token, "운영체제 중간고사", "2026-11-13T13:00:00+09:00");

        mockMvc.perform(post("/api/v1/exam-periods").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "중간고사", "from": "2026-11-09", "to": "2026-11-14",
                                  "exams": [ { "eventId": %d, "difficulty": 3, "studyHoursNeeded": 6 } ] }
                                """.formatted(examEventId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.constraints.minSleepMin").value(300))
                .andExpect(jsonPath("$.data.constraints.includeNaps").value(true));
    }

    @Test
    void createExamPeriod_overlappingExistingPeriod_returnsConflict() throws Exception {
        String token = signupAndLogin("examuser3");
        Integer examEventId = createExamEvent(token, "중간고사", "2026-11-12T10:00:00+09:00");

        mockMvc.perform(post("/api/v1/exam-periods").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "1차", "from": "2026-11-09", "to": "2026-11-14",
                                  "exams": [ { "eventId": %d, "difficulty": 3, "studyHoursNeeded": 6 } ] }
                                """.formatted(examEventId)))
                .andExpect(status().isCreated());

        Integer secondExamEventId = createExamEvent(token, "기말고사", "2026-11-13T10:00:00+09:00");
        mockMvc.perform(post("/api/v1/exam-periods").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "2차(겹침)", "from": "2026-11-13", "to": "2026-11-16",
                                  "exams": [ { "eventId": %d, "difficulty": 3, "studyHoursNeeded": 6 } ] }
                                """.formatted(secondExamEventId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXAM_PERIOD_OVERLAP"));
    }

    @Test
    void createExamPeriod_withExamDateOutsideRange_returnsValidationFailed() throws Exception {
        String token = signupAndLogin("examuser4");
        Integer examEventId = createExamEvent(token, "중간고사", "2026-11-20T10:00:00+09:00");

        mockMvc.perform(post("/api/v1/exam-periods").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "중간고사", "from": "2026-11-09", "to": "2026-11-14",
                                  "exams": [ { "eventId": %d, "difficulty": 3, "studyHoursNeeded": 6 } ] }
                                """.formatted(examEventId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createExamPeriod_withEventOfOtherUser_returnsForbidden() throws Exception {
        String ownerToken = signupAndLogin("examowner1");
        String otherToken = signupAndLogin("examother1");
        Integer examEventId = createExamEvent(ownerToken, "중간고사", "2026-11-12T10:00:00+09:00");

        mockMvc.perform(post("/api/v1/exam-periods").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "남의 시험", "from": "2026-11-09", "to": "2026-11-14",
                                  "exams": [ { "eventId": %d, "difficulty": 3, "studyHoursNeeded": 6 } ] }
                                """.formatted(examEventId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void createExamPeriod_withNonExamEvent_returnsEventNotFound() throws Exception {
        String token = signupAndLogin("examuser5");
        String response = mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "PERSONAL", "title": "동아리 행사", "startAt": "2026-11-12T10:00:00+09:00" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer personalEventId = com.jayway.jsonpath.JsonPath.read(response, "$.data.event.eventId");

        mockMvc.perform(post("/api/v1/exam-periods").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "시험아님", "from": "2026-11-09", "to": "2026-11-14",
                                  "exams": [ { "eventId": %d, "difficulty": 3, "studyHoursNeeded": 6 } ] }
                                """.formatted(personalEventId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }
}
