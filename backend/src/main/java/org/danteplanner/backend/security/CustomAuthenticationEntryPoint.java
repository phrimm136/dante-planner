package org.danteplanner.backend.security;

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
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String AUTH_ERROR_ATTRIBUTE = "auth.error";
    private static final String DEFAULT_ERROR_CODE = "UNAUTHORIZED";
    private static final String DEFAULT_ERROR_MESSAGE = "Authentication required";

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                objectMapper.writeValueAsString(Map.of("message", DEFAULT_ERROR_MESSAGE))
        );
    }
}
