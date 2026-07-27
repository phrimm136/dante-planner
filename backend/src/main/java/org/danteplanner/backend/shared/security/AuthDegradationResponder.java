package org.danteplanner.backend.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Reports a datastore outage met while authenticating, before the request reaches any
 * {@code @ControllerAdvice}.
 *
 * <p>The status is the point. A 503 tells the client the condition is transient and retryable,
 * where a 500 or a silent downgrade to guest would not; nginx rewrites the body for external
 * clients, so only the status and code cross the edge intact.</p>
 */
@Component
@RequiredArgsConstructor
public class AuthDegradationResponder {

    private final ObjectMapper objectMapper;

    /**
     * Report the database as unreachable.
     *
     * @param response the response to write the 503 to
     * @throws IOException if the response is already committed or the client is gone
     */
    public void writeDbUnavailable(HttpServletResponse response) throws IOException {
        write(response, DegradationErrorConstants.DB_UNAVAILABLE_CODE,
                DegradationErrorConstants.DB_UNAVAILABLE_MESSAGE);
    }

    /**
     * Report the auth store (Redis) as unreachable, a distinct retryable condition from a database
     * outage.
     *
     * @param response the response to write the 503 to
     * @throws IOException if the response is already committed or the client is gone
     */
    public void writeAuthUnavailable(HttpServletResponse response) throws IOException {
        write(response, DegradationErrorConstants.AUTH_UNAVAILABLE_CODE,
                DegradationErrorConstants.AUTH_UNAVAILABLE_MESSAGE);
    }

    /**
     * Writes the JSON body through the ObjectMapper so a code or message can never escape unescaped.
     */
    private void write(HttpServletResponse response, String code, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.getWriter().write(
                objectMapper.writeValueAsString(Map.of("error", code, "message", message))
        );
    }
}
