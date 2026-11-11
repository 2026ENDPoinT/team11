package com.team11.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
class UserControllerTest {

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
                    { "type": "PRIVACY", "agreed": true },
                    { "type": "HEALTH_DATA", "agreed": true }
                  ]
                }
                """.formatted(loginId, loginId);
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated());

        String loginBody = """
                { "loginId": "%s", "password": "P@ssw0rd1" }
                """.formatted(loginId);
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(loginResponse, "$.data.accessToken");
    }

    @Test
    void profile_get_and_patch() throws Exception {
        String token = signupAndLogin("userprofile1");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("userprofile1"))
                .andExpect(jsonPath("$.data.settings.sleepNeedMin").value(465))
                .andExpect(jsonPath("$.data.character.characterId").value(1));

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "chronotype": "EVENING", "commuteMin": 45 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.chronotype").value("EVENING"))
                .andExpect(jsonPath("$.data.settings.commuteMin").value(45));

        // 범위를 벗어난 값은 VALIDATION_FAILED
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sleepNeedMin": 100 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void profile_patch_withDuplicateNickname_returnsNicknameDuplicated() throws Exception {
        signupAndLogin("owner1");
        String token = signupAndLogin("other1");

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "owner1" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
    }

    @Test
    void onboarding_computesChronotypeAndSuggestedSettings_perSpecExample() throws Exception {
        String token = signupAndLogin("onboard1");

        mockMvc.perform(post("/api/v1/users/me/onboarding").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "weekdayBedTime": "01:00", "weekdayWakeTime": "07:30",
                                  "freedayBedTime": "02:00", "freedayWakeTime": "10:00",
                                  "sleepLatencyMin": 15, "commuteMin": 40, "prepMin": 55, "caffeinePerDay": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chronotype").value("EVENING"))
                .andExpect(jsonPath("$.data.midSleepFreeday").value("06:00"))
                .andExpect(jsonPath("$.data.suggestedSettings.sleepNeedMin").value(480))
                .andExpect(jsonPath("$.data.onboardingDone").value(true));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.onboardingDone").value(true))
                .andExpect(jsonPath("$.data.settings.sleepNeedMin").value(480));
    }

    @Test
    void notificationSettings_get_default_and_put() throws Exception {
        String token = signupAndLogin("notif1");

        mockMvc.perform(get("/api/v1/users/me/notification-settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.windDownMinBefore").value(60))
                .andExpect(jsonPath("$.data.quietHours.start").value("00:30"));

        mockMvc.perform(put("/api/v1/users/me/notification-settings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true, "windDownMinBefore": 30, "bedReminderEnabled": true,
                                  "caffeineCutoffEnabled": false, "napReminderEnabled": true,
                                  "streakRiskEnabled": true, "weeklyReportEnabled": true,
                                  "quietHours": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windDownMinBefore").value(30))
                .andExpect(jsonPath("$.data.caffeineCutoffEnabled").value(false))
                .andExpect(jsonPath("$.data.quietHours").value(nullValue()));
    }

    @Test
    void consents_defaultFour_thenRevokeHealthData_thenTermsIsNotRevocable() throws Exception {
        String token = signupAndLogin("consent1");

        mockMvc.perform(get("/api/v1/users/me/consents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consents", hasSize(4)));

        mockMvc.perform(put("/api/v1/users/me/consents").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "consents": [ { "type": "HEALTH_DATA", "agreed": false } ] }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/me/consents").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "consents": [ { "type": "TERMS", "agreed": false } ] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CONSENT_NOT_REVOCABLE"));
    }

    @Test
    void characters_list_select_and_selectMissing() throws Exception {
        String token = signupAndLogin("char1");

        mockMvc.perform(get("/api/v1/characters").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characters", hasSize(2)))
                .andExpect(jsonPath("$.data.selectedCharacterId").value(1));

        mockMvc.perform(put("/api/v1/users/me/character").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "characterId": 2 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.character.characterId").value(2));

        mockMvc.perform(put("/api/v1/users/me/character").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "characterId": 999 }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHARACTER_NOT_FOUND"));
    }

    @Test
    void deleteAccount_wrongPasswordThenSuccess() throws Exception {
        String loginId = "withdraw1";
        String token = signupAndLogin(loginId);

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "password": "wrong1234" }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "password": "P@ssw0rd1" }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "loginId": "%s", "password": "P@ssw0rd1" }
                                """.formatted(loginId)))
                .andExpect(status().isUnauthorized());
    }
}
