package com.team11.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AppConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAppConfig_withoutAuth_returnsFeatureFlagsAndMinSleepMin() throws Exception {
        mockMvc.perform(get("/api/v1/app/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minSleepMin").value(240))
                .andExpect(jsonPath("$.data.maintenance.active").value(false))
                .andExpect(jsonPath("$.data.featureFlags.examMode").value(false))
                .andExpect(jsonPath("$.data.featureFlags.eclassSync").value(false))
                .andExpect(jsonPath("$.data.featureFlags.wearableSync").value(true))
                .andExpect(jsonPath("$.data.featureFlags.assistant").value(false))
                .andExpect(jsonPath("$.data.featureFlags.tips").value(false));
    }

    @Test
    void getActuatorHealth_withoutAuth_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
