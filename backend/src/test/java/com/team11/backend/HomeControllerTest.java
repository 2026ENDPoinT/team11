package com.team11.backend;

import static org.hamcrest.Matchers.nullValue;
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
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void getHome_withoutHealthDataConsent_returnsConsentRequired() throws Exception {
        String token = signupAndLogin("homenoconsent1", false);

        mockMvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void getHome_happyPath_assemblesTonightStreakAndOpenSleepLog() throws Exception {
        String token = signupAndLogin("homeflow1", true);

        mockMvc.perform(post("/api/v1/sleep-logs/start").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tonight.mode").value("FREE"))
                .andExpect(jsonPath("$.data.tonight.recommendedBedAt").exists())
                .andExpect(jsonPath("$.data.streak.currentCount").value(0))
                .andExpect(jsonPath("$.data.openSleepLog.sleepLogId").exists())
                .andExpect(jsonPath("$.data.openSleepLog.bedAt").exists())
                .andExpect(jsonPath("$.data.character.characterId").exists())
                .andExpect(jsonPath("$.data.sleepDebtMin").value(nullValue()))
                .andExpect(jsonPath("$.data.tip.tipId").exists())
                .andExpect(jsonPath("$.data.tip.text").exists())
                .andExpect(jsonPath("$.data.unreadNotifications").value(0))
                .andExpect(jsonPath("$.data.nextEvents").isArray());
    }
}
