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
class TimetableControllerTest {

    @Autowired
    MockMvc mockMvc;

    private static final String ENTRY = """
            { "semester": "2026-2", "dayOfWeek": "%s", "startTime": "%s", "endTime": "%s", "courseName": "%s", "room": "미래관-319" }
            """;

    @Test
    void crud_overlap_andList() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "ttcrud0001");

        String created = mockMvc.perform(post("/api/v1/timetable/entries").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENTRY.formatted("THU", "09:00", "13:00", "오픈소스소프트웨어")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.room").value("미래관-319"))
                .andReturn().getResponse().getContentAsString();
        int entryId = JsonPath.read(created, "$.data.entryId");

        // 겹치는 수업
        mockMvc.perform(post("/api/v1/timetable/entries").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENTRY.formatted("THU", "12:00", "14:00", "겹치는수업")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIMETABLE_OVERLAP"))
                .andExpect(jsonPath("$.error.details.conflicts[0].courseName").value("오픈소스소프트웨어"));

        // 시작 >= 종료
        mockMvc.perform(post("/api/v1/timetable/entries").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENTRY.formatted("FRI", "13:00", "13:00", "역순")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TIME_RANGE_INVALID"));

        mockMvc.perform(put("/api/v1/timetable/entries/" + entryId).with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENTRY.formatted("THU", "10:00", "12:00", "오픈소스소프트웨어(수정)")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startTime").value("10:00:00"))
                .andExpect(jsonPath("$.data.courseName").value("오픈소스소프트웨어(수정)"));

        mockMvc.perform(get("/api/v1/timetable").with(bearer(token)).param("semester", "2026-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.semester").value("2026-2"))
                .andExpect(jsonPath("$.data.entries.length()").value(1));

        mockMvc.perform(get("/api/v1/timetable").with(bearer(token)).param("semester", "2030-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SEMESTER_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/timetable/entries/" + entryId).with(bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/timetable/entries/" + entryId).with(bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUsersEntry_isForbidden() throws Exception {
        String owner = ApiTestSupport.onboardedUser(mockMvc, "ttowner001");
        String other = ApiTestSupport.onboardedUser(mockMvc, "ttother001");
        String created = mockMvc.perform(post("/api/v1/timetable/entries").with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(ENTRY.formatted("MON", "09:00", "10:00", "내수업")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int entryId = JsonPath.read(created, "$.data.entryId");

        mockMvc.perform(delete("/api/v1/timetable/entries/" + entryId).with(bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void semesters_andEverytimeUrlValidation() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "ttsemest01");
        mockMvc.perform(get("/api/v1/semesters").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.semesters[0].semester").value("2026-2"));

        mockMvc.perform(post("/api/v1/timetable/import/everytime").with(bearer(token)).param("semester", "2026-2")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "url": "https://everytime.kr/timetable" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EVERYTIME_URL_INVALID"));
    }
}
