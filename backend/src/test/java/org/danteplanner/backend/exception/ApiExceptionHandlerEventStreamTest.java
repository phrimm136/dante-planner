package org.danteplanner.backend.exception;

import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.exception.StreamLifecycleExceptionAdvice;
import org.danteplanner.backend.shared.ratelimit.RateLimitExceededException;
import org.danteplanner.backend.shared.sse.SseCapacityExceededException;
import org.danteplanner.backend.shared.sse.SseConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Every error raised on an endpoint declaring {@code produces=text/event-stream} must reach the
 * client under its own status, as a problem document.
 *
 * <p>The matched endpoint's producible media types outrank the client's Accept header during
 * content negotiation, so a rendered error body has no acceptable converter and the status escapes
 * to the container as a 500. One endpoint per error family holds that guarantee across the whole
 * advice rather than for the two families that were hand-written around it.</p>
 */
class ApiExceptionHandlerEventStreamTest {

    private static final int MAX_CONNECTIONS = 500;
    private static final String DEGRADED_RETRY_AFTER = "10";

    @RestController
    static class EventStreamThrowingController {

        @GetMapping(value = "/probe/{id}/absent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter absent(@PathVariable UUID id) {
            throw new PlannerNotFoundException(id);
        }

        @GetMapping(value = "/probe/{id}/typed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter typed(@PathVariable UUID id) {
            return new SseEmitter();
        }

        @GetMapping(value = "/probe/{id}/full", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter full(@PathVariable UUID id) {
            throw new SseCapacityExceededException(id, MAX_CONNECTIONS);
        }

        @GetMapping(value = "/probe/throttled", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter throttled() {
            throw new RateLimitExceededException(815L, "probe");
        }

        @GetMapping(value = "/probe/degraded", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter degraded() {
            throw new DataAccessResourceFailureException("Unable to acquire JDBC connection");
        }

        @GetMapping(value = "/probe/revoked", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter revoked() {
            throw new SessionRevokedException("family-7");
        }

        @GetMapping(value = "/probe/expired", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter expired() {
            throw new AsyncRequestTimeoutException();
        }

        @GetMapping(value = "/probe/unexpected", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter unexpected() {
            throw new IllegalStateException("something nobody mapped");
        }
    }

    private CookieUtils cookieUtils;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cookieUtils = mock(CookieUtils.class);
        mockMvc = standaloneSetup(new EventStreamThrowingController())
                .setControllerAdvice(new ApiExceptionHandler(cookieUtils), new StreamLifecycleExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("an absent planner answers 404 as a problem document")
    void absentPlanner_WhenAcceptIsEventStream_AnswersProblemNotFound() throws Exception {
        mockMvc.perform(get("/probe/{id}/absent", UUID.randomUUID()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("a malformed path id answers 404, not 500")
    void malformedPathId_WhenAcceptIsEventStream_AnswersNotFound() throws Exception {
        mockMvc.perform(get("/probe/{id}/typed", "not-a-uuid").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("a full registry answers 429 and names the wait")
    void fullRegistry_WhenAcceptIsEventStream_AnswersTooManyRequests() throws Exception {
        mockMvc.perform(get("/probe/{id}/full", UUID.randomUUID()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER,
                        String.valueOf(SseConstants.CAPACITY_RETRY_AFTER_SECONDS)))
                .andExpect(jsonPath("$.code").value("SSE_CAPACITY_EXCEEDED"));
    }

    @Test
    @DisplayName("an exhausted bucket answers 429")
    void exhaustedBucket_WhenAcceptIsEventStream_AnswersTooManyRequests() throws Exception {
        mockMvc.perform(get("/probe/throttled").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("an unreachable database answers 503 and names the wait")
    void unreachableDatabase_WhenAcceptIsEventStream_AnswersServiceUnavailable() throws Exception {
        mockMvc.perform(get("/probe/degraded").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, DEGRADED_RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("WRITE_TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("a revoked session answers 401 and still clears the auth cookies")
    void revokedSession_WhenAcceptIsEventStream_AnswersUnauthorizedAndClearsCookies() throws Exception {
        mockMvc.perform(get("/probe/revoked").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(cookieUtils).clearAuthCookies(any());
    }

    @Test
    @DisplayName("an unmapped failure answers 500 as a problem document")
    void unmappedFailure_WhenAcceptIsEventStream_AnswersInternalError() throws Exception {
        mockMvc.perform(get("/probe/unexpected").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("a timed-out stream is swallowed, leaving the response untouched")
    void timedOutStream_WhenAcceptIsEventStream_WritesNothing() throws Exception {
        mockMvc.perform(get("/probe/expired").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }
}
