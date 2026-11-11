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
class EventControllerTest {

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

    @Test
    void event_create_list_get_update_delete() throws Exception {
        String token = signupAndLogin("evuser1");

        String createResponse = mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "PERSONAL", "title": "동기 술자리",
                                  "startAt": "2026-11-13T19:00:00+09:00", "endAt": "2026-11-13T23:00:00+09:00",
                                  "tag": "DRINKING", "note": "2차 갈 수도" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.event.title").value("동기 술자리"))
                .andExpect(jsonPath("$.data.event.repeat.freq").value("NONE"))
                .andExpect(jsonPath("$.data.affectedPlans", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        Integer eventId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.event.eventId");

        mockMvc.perform(get("/api/v1/events").param("from", "2026-11-09").param("to", "2026-11-15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events", hasSize(1)));

        // 범위 밖이면 안 보임
        mockMvc.perform(get("/api/v1/events").param("from", "2026-12-01").param("to", "2026-12-07")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.events", hasSize(0)));

        mockMvc.perform(get("/api/v1/events/" + eventId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tag").value("DRINKING"));

        mockMvc.perform(patch("/api/v1/events/" + eventId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "endAt": "2026-11-13T22:00:00+09:00", "note": "1차만" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.note").value("1차만"));

        mockMvc.perform(delete("/api/v1/events/" + eventId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/events/" + eventId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void getEvents_withRangeOver93Days_returnsDateRangeTooLarge() throws Exception {
        String token = signupAndLogin("evrange1");

        mockMvc.perform(get("/api/v1/events").param("from", "2026-01-01").param("to", "2026-12-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DATE_RANGE_TOO_LARGE"));
    }

    @Test
    void updateEvent_ofOtherUser_returnsForbidden() throws Exception {
        String ownerToken = signupAndLogin("evowner1");
        String otherToken = signupAndLogin("evother1");

        String createResponse = mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "EXAM", "title": "중간고사", "startAt": "2026-11-12T10:00:00+09:00" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer eventId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.event.eventId");

        mockMvc.perform(patch("/api/v1/events/" + eventId).header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "note": "해킹시도" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void createEvent_withEndAtBeforeStartAt_returnsTimeRangeInvalid() throws Exception {
        String token = signupAndLogin("evtimebad1");

        mockMvc.perform(post("/api/v1/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "PERSONAL", "title": "시간역전", "startAt": "2026-11-13T19:00:00+09:00",
                                  "endAt": "2026-11-13T18:00:00+09:00" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TIME_RANGE_INVALID"));
    }
}
