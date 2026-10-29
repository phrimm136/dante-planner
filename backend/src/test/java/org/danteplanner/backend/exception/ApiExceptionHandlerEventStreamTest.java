package org.danteplanner.backend.exception;

import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.auth.security.SessionRevokedExceptionAdvice;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.exception.StreamLifecycleExceptionAdvice;
import org.danteplanner.backend.shared.ratelimit.RateLimitExceededException;
import org.danteplanner.backend.shared.sse.SseCapacityExceededException;
import org.danteplanner.backend.shared.sse.SseConstants;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
            throw new RateLimitExceededException(815L, "probe", 1L);
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

        @GetMapping(value = "/probe/missing", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/probe/missing", "/probe/missing");
        }

        @GetMapping(value = "/probe/unexpected", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter unexpected() {
            throw new IllegalStateException("something nobody mapped");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CookieUtils cookieUtils = new CookieUtils(false, "", "Lax");
        ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();
        mockMvc = standaloneSetup(new EventStreamThrowingController())
                .setControllerAdvice(apiExceptionHandler, new StreamLifecycleExceptionAdvice(),
                        new SessionRevokedExceptionAdvice(cookieUtils, apiExceptionHandler))
                .build();
    }

    /**
     * The {@code message} property mirrors {@code detail} for the deploy window, and the body
     * always names its own status.
     */
    private static ResultMatcher rendersOperableBody() {
        return result -> {
            String content = result.getResponse().getContentAsString();
            String detail = JsonPath.read(content, "$.detail");
            String message = JsonPath.read(content, "$.message");
            int status = JsonPath.read(content, "$.status");
            assertThat(message).as("message mirrors detail").isEqualTo(detail);
            assertThat(status).as("the body names its own status").isEqualTo(result.getResponse().getStatus());
        };
    }

    @Test
    void absentPlanner_WhenAcceptIsEventStream_AnswersProblemNotFound() throws Exception {
        mockMvc.perform(get("/probe/{id}/absent", UUID.randomUUID()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(rendersOperableBody());
    }

    @Test
    void malformedPathId_WhenAcceptIsEventStream_AnswersNotFound() throws Exception {
        mockMvc.perform(get("/probe/{id}/typed", "not-a-uuid").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(rendersOperableBody());
    }

    @Test
    void fullRegistry_WhenAcceptIsEventStream_AnswersTooManyRequestsNamingTheWait() throws Exception {
        mockMvc.perform(get("/probe/{id}/full", UUID.randomUUID()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER,
                        String.valueOf(SseConstants.CAPACITY_RETRY_AFTER_SECONDS)))
                .andExpect(jsonPath("$.code").value("SSE_CAPACITY_EXCEEDED"))
                .andExpect(rendersOperableBody());
    }

    @Test
    void exhaustedBucket_WhenAcceptIsEventStream_AnswersTooManyRequests() throws Exception {
        mockMvc.perform(get("/probe/throttled").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(rendersOperableBody());
    }

    @Test
    void unreachableDatabase_WhenAcceptIsEventStream_AnswersServiceUnavailableNamingTheWait() throws Exception {
        mockMvc.perform(get("/probe/degraded").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, DEGRADED_RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("WRITE_TEMPORARILY_UNAVAILABLE"))
                .andExpect(rendersOperableBody());
    }

    @Test
    void revokedSession_WhenAcceptIsEventStream_AnswersUnauthorizedAndClearsCookies() throws Exception {
        mockMvc.perform(get("/probe/revoked").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(rendersOperableBody())
                .andExpect(cookie().maxAge(CookieConstants.ACCESS_TOKEN, 0))
                .andExpect(cookie().maxAge(CookieConstants.REFRESH_TOKEN, 0));
    }

    @Test
    void unmappedFailure_WhenAcceptIsEventStream_AnswersInternalError() throws Exception {
        mockMvc.perform(get("/probe/unexpected").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(rendersOperableBody());
    }

    @Test
    void unroutedPath_WhenAcceptIsEventStream_AnswersProblemNotFound() throws Exception {
        mockMvc.perform(get("/probe/missing").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Resource not found"))
                .andExpect(rendersOperableBody());
    }

    @Test
    void timedOutStream_WhenAcceptIsEventStream_WritesNothing() throws Exception {
        mockMvc.perform(get("/probe/expired").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }
}
