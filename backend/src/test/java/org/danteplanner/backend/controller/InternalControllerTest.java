package org.danteplanner.backend.controller;

import org.danteplanner.backend.config.LineageRotationFlag;
import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the internal feature-flag toggle endpoint.
 *
 * <p>Exercises {@code POST /api/internal/feature-flags/lineage-rotation} against the
 * real {@link LineageRotationFlag} so the API-key gate and the live flag mutation are
 * verified end-to-end without mocking.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "internal.api-key=test-internal-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
class InternalControllerTest {

    private static final String ENDPOINT = "/api/internal/feature-flags/lineage-rotation";
    private static final String KEY_HEADER = "X-Internal-Api-Key";
    private static final String VALID_KEY = "test-internal-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LineageRotationFlag lineageRotationFlag;

    @BeforeEach
    void setUp() {
        lineageRotationFlag.setEnabled(false);
    }

    @Nested
    @DisplayName("POST /api/internal/feature-flags/lineage-rotation - valid key")
    class ValidKey {

        @Test
        void setLineageRotation_WhenValidKeyAndEnabledTrue_Returns200AndEnablesFlag() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .header(KEY_HEADER, VALID_KEY)
                            .param("enabled", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"))
                    .andExpect(jsonPath("$.lineageRotationEnabled").value("true"));

            assertThat(lineageRotationFlag.isEnabled()).isTrue();
        }

        @Test
        void setLineageRotation_WhenValidKeyAndEnabledFalse_Returns200AndDisablesFlag() throws Exception {
            lineageRotationFlag.setEnabled(true);

            mockMvc.perform(post(ENDPOINT)
                            .header(KEY_HEADER, VALID_KEY)
                            .param("enabled", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lineageRotationEnabled").value("false"));

            assertThat(lineageRotationFlag.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("POST /api/internal/feature-flags/lineage-rotation - rejected key")
    class RejectedKey {

        @Test
        void setLineageRotation_WhenWrongKey_Returns403AndLeavesFlagUnchanged() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .header(KEY_HEADER, "wrong-key")
                            .param("enabled", "true"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Invalid API key"));

            assertThat(lineageRotationFlag.isEnabled()).isFalse();
        }

        @Test
        void setLineageRotation_WhenBlankKey_Returns403AndLeavesFlagUnchanged() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .header(KEY_HEADER, "")
                            .param("enabled", "true"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Invalid API key"));

            assertThat(lineageRotationFlag.isEnabled()).isFalse();
        }
    }
}
