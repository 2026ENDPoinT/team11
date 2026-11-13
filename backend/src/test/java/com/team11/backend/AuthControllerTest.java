package com.team11.backend;

import static com.team11.backend.ApiTestSupport.PASSWORD;
import static com.team11.backend.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.notNullValue;
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
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void signup_login_refreshRotation() throws Exception {
        String loginId = "authhappy1";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        { "name": "한결", "loginId": "%s", "password": "%s" }
                        """.formatted(loginId, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.loginId").value(loginId))
                .andExpect(jsonPath("$.data.name").value("한결"));

        // 중복 아이디
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        { "name": "다른사람", "loginId": "%s", "password": "%s" }
                        """.formatted(loginId, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_DUPLICATE_LOGIN_ID"));

        String loginResponse = ApiTestSupport.login(mockMvc, loginId);
        String refreshToken = JsonPath.read(loginResponse, "$.data.refreshToken");
        org.assertj.core.api.Assertions.assertThat((Boolean) JsonPath.read(loginResponse, "$.data.user.onboardingDone")).isFalse();

        String refreshBody = """
                { "refreshToken": "%s" }
                """.formatted(refreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));

        // 회전된 토큰 재사용 = 탈취로 간주
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void signup_invalidPassword_returnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        { "name": "한결", "loginId": "weakpw01", "password": "short" }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("password"));
    }

    @Test
    void login_fiveFailures_locksAccount() throws Exception {
        String loginId = "lockme0001";
        ApiTestSupport.signup(mockMvc, loginId);
        String wrong = """
                { "loginId": "%s", "password": "wrongpass1" }
                """.formatted(loginId);
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        }
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error.details.retryAfterSec", notNullValue()));

        // 잠긴 동안엔 맞는 비밀번호도 거부
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "loginId": "%s", "password": "%s" }
                        """.formatted(loginId, PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void changePassword_thenLoginWithNew_andLogout() throws Exception {
        String loginId = "changepw01";
        ApiTestSupport.signup(mockMvc, loginId);
        String loginResponse = ApiTestSupport.login(mockMvc, loginId);
        String accessToken = JsonPath.read(loginResponse, "$.data.accessToken");
        String refreshToken = JsonPath.read(loginResponse, "$.data.refreshToken");

        mockMvc.perform(put("/api/v1/auth/password").with(bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "currentPassword": "nope12345", "newPassword": "NewPass123" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_PASSWORD_MISMATCH"));

        mockMvc.perform(put("/api/v1/auth/password").with(bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "currentPassword": "%s", "newPassword": "NewPass123" }
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        // 비번 변경 시 refreshToken을 안 보냈으니 기존 세션은 전부 무효
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                        { "refreshToken": "%s" }
                        """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());

        String newLogin = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "loginId": "%s", "password": "NewPass123" }
                        """.formatted(loginId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newAccess = JsonPath.read(newLogin, "$.data.accessToken");
        String newRefresh = JsonPath.read(newLogin, "$.data.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout").with(bearer(newAccess))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "refreshToken": "%s" }
                                """.formatted(newRefresh)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                        { "refreshToken": "%s" }
                        """.formatted(newRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_cannotBeUsedAsAccessToken() throws Exception {
        String loginId = "refreshas01";
        ApiTestSupport.signup(mockMvc, loginId);
        String refreshToken = JsonPath.read(ApiTestSupport.login(mockMvc, loginId), "$.data.refreshToken");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me")
                        .with(bearer(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }
}
