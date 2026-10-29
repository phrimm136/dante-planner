package org.danteplanner.backend.shared.security;

import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.support.TestJsonMappers;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.user.api.UserBannedException;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = TestJsonMappers.springDefaults();
    private final ProblemAccessDeniedHandler handler = new ProblemAccessDeniedHandler(new ProblemWriter(objectMapper));

    @Test
    void handle_WhenDenialCarriesADomainRefusal_WritesTheRefusalBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("POST", "/api/moderation/users"), response,
                new AccessDeniedException("denied", new UserBannedException(7L, Instant.EPOCH)));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body.path("code").asString()).isEqualTo("USER_BANNED");
        assertThat(body.path("instance").asString()).isEqualTo("/api/moderation/users");
    }

    @Test
    void handle_WhenDenialCarriesNoRefusal_WritesAForbiddenProblem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("GET", "/api/admin/user/1/role"), response,
                new AccessDeniedException("denied"));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body.path("code").asString()).isEqualTo("FORBIDDEN");
    }

    @Test
    void handle_WhenRefusalCarriesHeaders_WritesThemWithTheBody() throws Exception {
        ErrorResponseException unavailable = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE);
        unavailable.getHeaders().set(HttpHeaders.RETRY_AFTER, "10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("GET", "/api/moderation/users"), response,
                new AccessDeniedException("denied", unavailable));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("10");
    }
}
