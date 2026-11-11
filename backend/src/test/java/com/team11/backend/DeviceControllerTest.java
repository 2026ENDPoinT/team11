package com.team11.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class DeviceControllerTest {

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
    void register_list_delete_fullFlow() throws Exception {
        String token = signupAndLogin("device1");

        String registerResponse = mockMvc.perform(post("/api/v1/devices").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "platform": "IOS", "pushToken": "ExponentPushToken[device1]", "appVersion": "1.0.0" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.platform").value("IOS"))
                .andReturn().getResponse().getContentAsString();
        Integer deviceId = com.jayway.jsonpath.JsonPath.read(registerResponse, "$.data.deviceId");

        // 같은 토큰 재등록은 갱신(200)
        mockMvc.perform(post("/api/v1/devices").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "platform": "IOS", "pushToken": "ExponentPushToken[device1]", "appVersion": "1.0.1" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value(deviceId));

        mockMvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devices", hasSize(1)))
                .andExpect(jsonPath("$.data.devices[0].appVersion").value("1.0.1"));

        mockMvc.perform(delete("/api/v1/devices/" + deviceId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devices", hasSize(0)));
    }

    @Test
    void register_withTokenOwnedByAnotherUser_transfersOwnership() throws Exception {
        String tokenA = signupAndLogin("devicea1");
        String tokenB = signupAndLogin("deviceb1");

        mockMvc.perform(post("/api/v1/devices").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "platform": "ANDROID", "pushToken": "ExponentPushToken[shared]" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/devices").header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "platform": "ANDROID", "pushToken": "ExponentPushToken[shared]" }
                                """))
                .andExpect(status().isOk());

        // A 입장에선 더 이상 자기 기기가 아님
        mockMvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devices", hasSize(0)));

        mockMvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devices", hasSize(1)));
    }

    @Test
    void delete_otherUsersDevice_returnsForbidden() throws Exception {
        String tokenA = signupAndLogin("deviceowner1");
        String tokenB = signupAndLogin("deviceother1");

        String registerResponse = mockMvc.perform(post("/api/v1/devices").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "platform": "IOS", "pushToken": "ExponentPushToken[ownerdevice]" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer deviceId = com.jayway.jsonpath.JsonPath.read(registerResponse, "$.data.deviceId");

        mockMvc.perform(delete("/api/v1/devices/" + deviceId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }
}
