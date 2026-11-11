package com.team11.backend;

import static org.hamcrest.Matchers.notNullValue;
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
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String SIGNUP_BODY = """
            {
              "loginId": "%s",
              "password": "P@ssw0rd1",
              "nickname": "한결",
              "consents": [
                { "type": "TERMS", "agreed": true },
                { "type": "PRIVACY", "agreed": true }
              ]
            }
            """;

    @Test
    void signup_login_refresh_happyPath() throws Exception {
        String loginId = "gksruf22";

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY.formatted(loginId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.loginId").value(loginId));

        mockMvc.perform(get("/api/v1/auth/check-login-id").param("loginId", loginId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));

        String loginBody = """
                { "loginId": "%s", "password": "P@ssw0rd1" }
                """.formatted(loginId);

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String refreshToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.data.refreshToken");

        String refreshBody = """
                { "refreshToken": "%s" }
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));

        // 회전된(=이미 사용된) refreshToken 재사용은 탈취로 간주해 거부
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void signup_withoutRequiredConsent_returnsConsentRequired() throws Exception {
        String body = """
                {
                  "loginId": "noconsent1",
                  "password": "P@ssw0rd1",
                  "nickname": "테스터",
                  "consents": [ { "type": "TERMS", "agreed": true } ]
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void login_withWrongPassword_returnsInvalidCredentials() throws Exception {
        String loginId = "wrongpw1";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY.formatted(loginId)))
                .andExpect(status().isCreated());

        String loginBody = """
                { "loginId": "%s", "password": "wrong1234" }
                """.formatted(loginId);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }
}
