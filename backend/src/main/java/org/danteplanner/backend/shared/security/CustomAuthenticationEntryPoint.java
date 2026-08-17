package org.danteplanner.backend.shared.security;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Custom authentication entry point that returns 401 with error details.
 *
 * <p>When JwtAuthenticationFilter encounters token errors, it sets the error code
 * as a request attribute and continues the filter chain. For public endpoints
 * (permitAll), the request proceeds normally. For protected endpoints, Spring
 * Security calls this entry point, which reads the error attribute and returns
 * a proper 401 response with the error code.</p>
 *
 * <p>This design separates authentication (filter) from authorization (SecurityConfig)
 * while preserving the frontend's token refresh contract.</p>
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Request attribute {@link JwtAuthenticationFilter} names the failure in. */
    public static final String AUTH_ERROR_ATTRIBUTE = "auth.error";

    /** The presented token was structurally unusable. */
    public static final String INVALID_TOKEN = "INVALID_TOKEN";

    /** The session behind the token was withdrawn: revoked family, logout, or a dead account. */
    public static final String SESSION_REVOKED = "SESSION_REVOKED";

    private static final String DEFAULT_ERROR_CODE = "UNAUTHORIZED";
    private static final String DEFAULT_ERROR_MESSAGE = "Authentication required";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        Object attribute = request.getAttribute(AUTH_ERROR_ATTRIBUTE);
        String code = attribute instanceof String named ? named : DEFAULT_ERROR_CODE;

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", code, "message", DEFAULT_ERROR_MESSAGE)));
    }
}
