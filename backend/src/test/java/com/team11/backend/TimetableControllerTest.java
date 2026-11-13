package com.team11.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class TimetableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String signupAndLogin(String loginId) throws Exception {
        String signupBody = """
                {
                  "loginId": "%s",
                  "password": "P@ssw0rd1",
                  "nickname": "%s",
                  "consents": [
                    { "type": "TERMS", "agreed": true },
                    { "type": "PRIVACY", "agreed": true }
                  ]
                }
                """.formatted(loginId, loginId);
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
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
    void semesters_returnsCurrentAndSortedDescending() throws Exception {
        String token = signupAndLogin("semuser1");

        mockMvc.perform(get("/api/v1/semesters").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.semesters", hasSize(4)))
                .andExpect(jsonPath("$.data.semesters[0].semester").value("2026-2"))
                .andExpect(jsonPath("$.data.semesters[0].current").value(true));
    }

    @Test
    void entry_create_get_update_delete_and_overlapRejected() throws Exception {
        String token = signupAndLogin("ttentry1");

        mockMvc.perform(post("/api/v1/timetable/entries").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "semester": "2026-2", "dayOfWeek": "MON", "startTime": "09:00", "endTime": "10:15",
                                  "courseName": "자료구조", "location": "미래관 301" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("MANUAL"));

        // 겹치는 수업은 거절
        mockMvc.perform(post("/api/v1/timetable/entries").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "semester": "2026-2", "dayOfWeek": "MON", "startTime": "09:30", "endTime": "10:30",
                                  "courseName": "알고리즘" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIMETABLE_OVERLAP"));

        // 끝나는 시각과 다음 수업 시작 시각이 같은 것은 허용
        String secondEntryResponse = mockMvc.perform(post("/api/v1/timetable/entries").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "semester": "2026-2", "dayOfWeek": "MON", "startTime": "10:15", "endTime": "11:00",
                                  "courseName": "운영체제" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer secondEntryId = com.jayway.jsonpath.JsonPath.read(secondEntryResponse, "$.data.entryId");

        mockMvc.perform(get("/api/v1/timetable").param("semester", "2026-2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries", hasSize(2)))
                .andExpect(jsonPath("$.data.entries[0].courseName").value("자료구조"))
                .andExpect(jsonPath("$.data.entries[1].courseName").value("운영체제"));

        mockMvc.perform(patch("/api/v1/timetable/entries/" + secondEntryId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "location": "미래관 302" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.location").value("미래관 302"));

        mockMvc.perform(delete("/api/v1/timetable/entries/" + secondEntryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/timetable").param("semester", "2026-2").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.entries", hasSize(1)));
    }

    @Test
    void updateEntry_ofOtherUser_returnsForbidden() throws Exception {
        String ownerToken = signupAndLogin("ttowner1");
        String otherToken = signupAndLogin("ttother1");

        String createResponse = mockMvc.perform(post("/api/v1/timetable/entries").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "semester": "2026-2", "dayOfWeek": "TUE", "startTime": "13:00", "endTime": "14:00",
                                  "courseName": "데이터베이스" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer entryId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.entryId");

        mockMvc.perform(patch("/api/v1/timetable/entries/" + entryId).header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "location": "해킹시도" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void bulkReplace_replacesOnlySameSourceAndRejectsInternalOverlap() throws Exception {
        String token = signupAndLogin("ttbulk1");

        // 기존 MANUAL 수업 하나 등록
        mockMvc.perform(post("/api/v1/timetable/entries").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "semester": "2026-2", "dayOfWeek": "WED", "startTime": "08:00", "endTime": "09:00",
                                  "courseName": "직접입력수업" }
                                """))
                .andExpect(status().isCreated());

        // 요청 내부에서 겹치면 전체 거절
        mockMvc.perform(put("/api/v1/timetable").param("semester", "2026-2").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "ECLASS",
                                  "entries": [
                                    { "externalId": "A", "dayOfWeek": "THU", "startTime": "09:00", "endTime": "10:00", "courseName": "A" },
                                    { "externalId": "B", "dayOfWeek": "THU", "startTime": "09:30", "endTime": "10:30", "courseName": "B" }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIMETABLE_OVERLAP"));

        // 정상 ECLASS 일괄 등록
        mockMvc.perform(put("/api/v1/timetable").param("semester", "2026-2").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "ECLASS",
                                  "entries": [
                                    { "externalId": "CSE2001-01", "dayOfWeek": "THU", "startTime": "09:00", "endTime": "10:15",
                                      "courseName": "자료구조", "location": "미래관 301" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries", hasSize(2))); // MANUAL 1개 + ECLASS 1개, 서로 다른 source라 유지됨
    }

    @Test
    void importFromEverytime_rejectsBlankAndMalformedUrlBeforeCallingExternalApi() throws Exception {
        String token = signupAndLogin("tteveryt1");

        mockMvc.perform(post("/api/v1/timetable/import/everytime").param("semester", "2026-2")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/timetable/import/everytime").param("semester", "2026-2")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://everytime.kr/timetable" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EVERYTIME_URL_INVALID"));
    }
}
