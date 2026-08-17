package org.danteplanner.backend.exception;
import org.danteplanner.backend.shared.exception.GlobalExceptionHandler;

import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.lettuce.core.RedisCommandTimeoutException;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Verifies that a rate-limit Redis stall is mapped to the typed 503 by
 * {@link GlobalExceptionHandler} regardless of which timeout fires first, through real
 * {@code @ExceptionHandler} dispatch.
 *
 * <p>The rate-limit path arms two equal timers per command: Lettuce's command timeout throws
 * {@link RedisCommandTimeoutException} (a {@code RedisException}); bucket4j's request timeout
 * throws its own {@link io.github.bucket4j.TimeoutException}, which is NOT a
 * {@code RedisException}. The latter was the branch that escaped to a catch-all 500. Using
 * {@code standaloneSetup} + {@code setControllerAdvice} exercises Spring's exception dispatch —
 * a direct call to the handler method would prove the body but not that the annotation routes
 * both types here.</p>
 */
class GlobalExceptionHandlerRateLimitRedisTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/boom/lettuce-timeout")
        public String lettuceCommandTimeout() {
            throw new RedisCommandTimeoutException("Command timed out after 3 second(s)");
        }

        @GetMapping("/boom/bucket4j-timeout")
        public String bucket4jRequestTimeout() {
            throw new io.github.bucket4j.TimeoutException("Violated timeout while waiting for redis future", 3_000_000_000L, 3_000_000_000L);
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(CookieUtils.class), new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
    }

    @Test
    @DisplayName("Lettuce command timeout (RedisException) maps to RATE_LIMIT_TEMPORARILY_UNAVAILABLE 503")
    void lettuceCommandTimeout_WhenThrown_MapsToRateLimitUnavailable503() throws Exception {
        mockMvc.perform(get("/boom/lettuce-timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("bucket4j request timeout (not a RedisException) maps to RATE_LIMIT_TEMPORARILY_UNAVAILABLE 503, not 500")
    void bucket4jRequestTimeout_WhenThrown_MapsToRateLimitUnavailable503() throws Exception {
        mockMvc.perform(get("/boom/bucket4j-timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_TEMPORARILY_UNAVAILABLE"));
    }
}
