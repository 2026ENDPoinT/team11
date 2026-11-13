package com.team11.backend;

import static com.team11.backend.ApiTestSupport.PASSWORD;
import static com.team11.backend.ApiTestSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    MockMvc mockMvc;

    @Test
    void onboarding_requiresAllRequiredConsents() throws Exception {
        String token = ApiTestSupport.signupAndLogin(mockMvc, "onbconsent1");
        mockMvc.perform(post("/api/v1/users/me/onboarding").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "consents": [ { "type": "TERMS", "agreed": true }, { "type": "PRIVACY", "agreed": true } ],
                                  "sleepNeedMin": 465, "commuteMin": 40, "prepMin": 50
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.error.details.missing").value("MEDICAL_DISCLAIMER"));

        mockMvc.perform(get("/api/v1/users/me").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingDone").value(false));
    }

    @Test
    void onboarding_appliesSettingsConsentsAndNotification() throws Exception {
        String token = ApiTestSupport.signupAndLogin(mockMvc, "onbhappy01");
        mockMvc.perform(post("/api/v1/users/me/onboarding").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(ApiTestSupport.ONBOARDING_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingDone").value(true))
                .andExpect(jsonPath("$.data.settings.sleepNeedMin").value(465))
                .andExpect(jsonPath("$.data.settings.commuteMin").value(40))
                .andExpect(jsonPath("$.data.settings.prepMin").value(50))
                .andExpect(jsonPath("$.data.settings.examPrepDays").value(3))
                .andExpect(jsonPath("$.data.settings.focusMinInBedMin").value(360));

        mockMvc.perform(get("/api/v1/users/me/consents").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consents.length()").value(4))
                .andExpect(jsonPath("$.data.consents[?(@.type=='NOTIFICATION')].agreed").value(true));

        mockMvc.perform(get("/api/v1/users/me/notification-settings").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wakeAlarmEnabled").value(true))
                .andExpect(jsonPath("$.data.bedtimeLeadMin").value(30));
    }

    @Test
    void settings_partialUpdate_andCrossValidation() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "settings001");

        mockMvc.perform(put("/api/v1/users/me/settings").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "focusMinInBedMin": 270, "focusMaxInBedMin": 360 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.focusMinInBedMin").value(270))
                .andExpect(jsonPath("$.data.focusMaxInBedMin").value(360))
                .andExpect(jsonPath("$.data.sleepNeedMin").value(465)); // 안 보낸 값은 유지

        mockMvc.perform(put("/api/v1/users/me/settings").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "focusMinInBedMin": 400 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("focusMinInBedMin"));

        mockMvc.perform(put("/api/v1/users/me/settings").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/users/me/settings").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "sleepNeedMin": 700 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void consents_requiredCannotBeRevoked_optionalCan() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "consents001");
        mockMvc.perform(put("/api/v1/users/me/consents").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "consents": [ { "type": "TERMS", "agreed": false } ] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CONSENT_NOT_REVOCABLE"));

        mockMvc.perform(put("/api/v1/users/me/consents").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "consents": [ { "type": "NOTIFICATION", "agreed": false } ] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consents[?(@.type=='NOTIFICATION')].agreed").value(false));
    }

    @Test
    void notificationSettings_leadMustBeOneOfOptions() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "notiset0001");
        mockMvc.perform(put("/api/v1/users/me/notification-settings").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "wakeAlarmEnabled": true, "bedtimeAlarmEnabled": false, "bedtimeLeadMin": 45 }
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/users/me/notification-settings").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "wakeAlarmEnabled": true, "bedtimeAlarmEnabled": false, "bedtimeLeadMin": 60 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bedtimeAlarmEnabled").value(false))
                .andExpect(jsonPath("$.data.bedtimeLeadMin").value(60));
    }

    @Test
    void rename_andDeleteAccount() throws Exception {
        String token = ApiTestSupport.onboardedUser(mockMvc, "deleteme001");
        mockMvc.perform(put("/api/v1/users/me").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "name": "새이름" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"));

        mockMvc.perform(delete("/api/v1/users/me").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "password": "wrongpass1" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));

        mockMvc.perform(delete("/api/v1/users/me").with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "password": "%s" }
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").with(bearer(token)))
                .andExpect(status().isUnauthorized());
    }
}
