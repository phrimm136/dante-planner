package org.danteplanner.backend.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.exception.GlobalExceptionHandler;
import org.danteplanner.backend.shared.sse.SseCapacityExceededException;
import org.danteplanner.backend.shared.sse.SseConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * A subscription refused because the planner's registry is full must reach the client as 429.
 *
 * <p>The endpoint declares {@code produces=text/event-stream}, so the response is written directly
 * rather than returned as a JSON {@code ResponseEntity}: content negotiation would find no
 * converter for that Accept header and the 429 would escape as a 500. {@code standaloneSetup}
 * exercises real {@code @ExceptionHandler} dispatch, so the negotiation step is reproduced.</p>
 */
class GlobalExceptionHandlerSseCapacityTest {

    private MockMvc mockMvc;

    @RestController
    static class FullRegistryController {
        @GetMapping(value = "/sse/full", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public String atCapacity() {
            throw new SseCapacityExceededException(UUID.randomUUID(), 500);
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new FullRegistryController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(CookieUtils.class), new ObjectMapper()))
                .build();
    }

    @Test
    @DisplayName("A refused text/event-stream subscription maps to 429, not 500")
    void refusedSseSubscription_WhenAcceptIsEventStream_MapsTo429() throws Exception {
        mockMvc.perform(get("/sse/full").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SSE_CAPACITY_EXCEEDED"));
    }

    @Test
    @DisplayName("A refused subscription names the wait, so the client does not retry sooner")
    void refusedSseSubscription_WhenRegistryIsFull_CarriesRetryAfter() throws Exception {
        mockMvc.perform(get("/sse/full").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER,
                        String.valueOf(SseConstants.CAPACITY_RETRY_AFTER_SECONDS)));
    }
}
