package org.danteplanner.backend.shared.security;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.exception.Problems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String AUTH_ERROR_ATTRIBUTE = "auth.error";

    public static final String INVALID_TOKEN = "INVALID_TOKEN";

    public static final String SESSION_REVOKED = "SESSION_REVOKED";

    private static final String DEFAULT_ERROR_CODE = "UNAUTHORIZED";
    private static final String DEFAULT_ERROR_MESSAGE = "Authentication required";

    private final ProblemWriter problemWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        Object attribute = request.getAttribute(AUTH_ERROR_ATTRIBUTE);
        String code = attribute instanceof String named ? named : DEFAULT_ERROR_CODE;

        problemWriter.write(request, response,
                Problems.fill(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED), code, DEFAULT_ERROR_MESSAGE),
                new HttpHeaders());
    }
}
