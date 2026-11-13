package com.team11.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 컨트롤러 테스트 공용: 가입→로그인→온보딩. 테스트 클래스들이 같은 컨텍스트/DB를 공유하므로 loginId는 테스트마다 유일하게. */
final class ApiTestSupport {

    static final String PASSWORD = "P@ssw0rd1";

    static final String ONBOARDING_BODY = """
            {
              "consents": [
                { "type": "MEDICAL_DISCLAIMER", "agreed": true },
                { "type": "TERMS", "agreed": true },
                { "type": "PRIVACY", "agreed": true }
              ],
              "sleepNeedMin": 465,
              "sleepLatencyMin": 15,
              "commuteMin": 40,
              "prepMin": 50,
              "examPrepDays": 3,
              "notificationAllowed": true
            }
            """;

    private ApiTestSupport() {
    }

    static void signup(MockMvc mvc, String loginId) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                { "name": "한결", "loginId": "%s", "password": "%s" }
                """.formatted(loginId, PASSWORD))).andExpect(status().isCreated());
    }

    static String login(MockMvc mvc, String loginId) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                { "loginId": "%s", "password": "%s" }
                """.formatted(loginId, PASSWORD))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 가입 + 로그인, accessToken 반환. */
    static String signupAndLogin(MockMvc mvc, String loginId) throws Exception {
        signup(mvc, loginId);
        return JsonPath.read(login(mvc, loginId), "$.data.accessToken");
    }

    /** 가입 + 로그인 + 기본 온보딩, accessToken 반환. */
    static String onboardedUser(MockMvc mvc, String loginId) throws Exception {
        String token = signupAndLogin(mvc, loginId);
        mvc.perform(post("/api/v1/users/me/onboarding").with(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(ONBOARDING_BODY)).andExpect(status().isOk());
        return token;
    }

    static RequestPostProcessor bearer(String accessToken) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + accessToken);
            return request;
        };
    }
}
