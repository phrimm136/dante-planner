package org.danteplanner.backend.exception;
import org.danteplanner.backend.shared.exception.GlobalExceptionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.ratelimit.RateLimitExceededException;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Regression: a rate-limited SSE subscription must map to 429, not 500.
 *
 * <p>SSE controllers declare {@code produces=text/event-stream}. When
 * {@link RateLimitExceededException} is thrown there and the handler returns a JSON
 * {@code ResponseEntity}, content negotiation finds no converter for the request's
 * {@code Accept: text/event-stream}, throws {@code HttpMediaTypeNotAcceptableException}, and the
 * original 429 escapes to Tomcat as a 500. Writing the response directly bypasses negotiation.
 * {@code standaloneSetup} exercises real {@code @ExceptionHandler} dispatch, so the negotiation
 * step is reproduced — a direct handler call would not surface the bug.</p>
 */
class GlobalExceptionHandlerRateLimitSseTest {

    private MockMvc mockMvc;

    @RestController
    static class SseThrowingController {
        @GetMapping(value = "/sse/boom", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public String rateLimited() {
            throw new RateLimitExceededException(815L, "sse");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new SseThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(CookieUtils.class), new ObjectMapper()))
                .build();
    }

    @Test
    @DisplayName("Rate-limited text/event-stream request maps to 429, not 500")
    void rateLimitedSseRequest_WhenAcceptIsEventStream_MapsTo429() throws Exception {
        mockMvc.perform(get("/sse/boom").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
