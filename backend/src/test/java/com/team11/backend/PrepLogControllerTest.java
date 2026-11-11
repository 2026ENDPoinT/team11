package com.team11.backend;

import static org.hamcrest.Matchers.hasSize;
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
class PrepLogControllerTest {

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

    @Test
    void create_computesPrepMin_andOverwritesSameDate() throws Exception {
        String token = signupAndLogin("preplog1");

        mockMvc.perform(post("/api/v1/prep-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "date": "2026-11-09", "wakeAt": "2026-11-09T07:05:00+09:00",
                                  "departureAt": "2026-11-09T08:02:00+09:00" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.prepLog.prepMin").value(57))
                .andExpect(jsonPath("$.data.prepMinEstimate").value(57));

        // 같은 날짜 재등록은 덮어쓰고 200
        mockMvc.perform(post("/api/v1/prep-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "date": "2026-11-09", "wakeAt": "2026-11-09T07:05:00+09:00",
                                  "departureAt": "2026-11-09T08:10:00+09:00" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prepLog.prepMin").value(65));

        mockMvc.perform(get("/api/v1/prep-logs").param("from", "2026-11-01").param("to", "2026-11-15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logs", hasSize(1)))
                .andExpect(jsonPath("$.data.avgPrepMin").value(65))
                .andExpect(jsonPath("$.data.prepMinEstimate").value(65));
    }

    @Test
    void create_withDepartureBeforeWake_returnsTimeRangeInvalid() throws Exception {
        String token = signupAndLogin("prepbad1");

        mockMvc.perform(post("/api/v1/prep-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "date": "2026-11-09", "wakeAt": "2026-11-09T08:00:00+09:00",
                                  "departureAt": "2026-11-09T07:00:00+09:00" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TIME_RANGE_INVALID"));
    }
}
