package org.danteplanner.backend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.exception.Problems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthDegradationResponder {

    private final ProblemWriter problemWriter;

    public void writeDbUnavailable(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        write(request, response, DegradationErrorConstants.DB_UNAVAILABLE);
    }

    public void writeAuthUnavailable(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        write(request, response, DegradationErrorConstants.AUTH_UNAVAILABLE);
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
            DegradationErrorConstants.Entry entry) throws IOException {
        SecurityContextHolder.clearContext();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, DegradationErrorConstants.RETRY_AFTER_SECONDS);
        problemWriter.write(request, response,
                Problems.fill(ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE),
                        entry.code(), entry.message()),
                headers);
    }
}
