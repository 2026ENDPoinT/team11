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
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void crud_rangeQuery_andMultiDayBand() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "evcrud0001");

        String exam = mockMvc.perform(post("/api/v1/events").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "title": "자료구조 중간고사", "type": "EXAM",
                                  "startAt": "2026-11-18T09:00:00+09:00", "endAt": "2026-11-18T10:00:00+09:00" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.startAt").value("2026-11-18T09:00:00+09:00")) // 오프셋 유지
                .andExpect(jsonPath("$.data.allDay").value(false))
                .andReturn().getResponse().getContentAsString();
        int examId = JsonPath.read(exam, "$.data.eventId");

        // 다일 종일 밴드(수업철회기간)
        mockMvc.perform(post("/api/v1/events").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "title": "수업철회기간", "type": "ETC", "allDay": true,
                                  "startAt": "2026-11-20T00:00:00+09:00", "endAt": "2026-11-24T00:00:00+09:00" }
                                """))
                .andExpect(status().isCreated());

        // 밴드 중간 날짜 하나만 조회해도 포함
        mockMvc.perform(get("/api/v1/events").with(bearer(token)).param("from", "2026-11-22").param("to", "2026-11-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].title").value("수업철회기간"));

        mockMvc.perform(get("/api/v1/events").with(bearer(token)).param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(2))
                .andExpect(jsonPath("$.data.events[0].eventId").value(examId));

        mockMvc.perform(get("/api/v1/events").with(bearer(token)).param("from", "2026-01-01").param("to", "2026-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DATE_RANGE_TOO_LARGE"));

        mockMvc.perform(put("/api/v1/events/" + examId).with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "title": "자료구조 중간고사(변경)", "type": "EXAM",
                                  "startAt": "2026-11-18T10:00:00+09:00", "endAt": "2026-11-18T09:00:00+09:00" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TIME_RANGE_INVALID"));

        mockMvc.perform(put("/api/v1/events/" + examId).with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "title": "자료구조 중간고사(변경)", "type": "EXAM",
                                  "startAt": "2026-11-18T10:00:00+09:00", "endAt": "2026-11-18T11:00:00+09:00" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("자료구조 중간고사(변경)"));

        mockMvc.perform(get("/api/v1/events/" + examId).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startAt").value("2026-11-18T10:00:00+09:00"));

        mockMvc.perform(delete("/api/v1/events/" + examId).with(bearer(token))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/events/" + examId).with(bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void otherUsersEvent_isForbidden() throws Exception {
        String owner = ApiTestSupport.onboardedUser(mockMvc, "evowner001");
        String other = ApiTestSupport.onboardedUser(mockMvc, "evother001");
        String created = mockMvc.perform(post("/api/v1/events").with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "title": "약속", "type": "PERSONAL", "startAt": "2026-11-18T19:00:00+09:00" }
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int eventId = JsonPath.read(created, "$.data.eventId");
        mockMvc.perform(get("/api/v1/events/" + eventId).with(bearer(other)))
                .andExpect(status().isForbidden());
    }
}
