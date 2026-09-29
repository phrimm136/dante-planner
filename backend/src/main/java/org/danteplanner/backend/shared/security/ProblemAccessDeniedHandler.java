package org.danteplanner.backend.shared.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.exception.Problems;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private static final String DEFAULT_ERROR_CODE = "FORBIDDEN";
    private static final String DEFAULT_ERROR_MESSAGE = "Access denied";

    private final ProblemWriter problemWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException denial) throws IOException {
        if (denial.getCause() instanceof ErrorResponse refusal) {
            log.warn("{}", denial.getCause().getMessage());
            problemWriter.write(request, response, refusal.getBody(), refusal.getHeaders());
            return;
        }
        problemWriter.write(request, response,
                Problems.fill(ProblemDetail.forStatus(HttpStatus.FORBIDDEN), DEFAULT_ERROR_CODE, DEFAULT_ERROR_MESSAGE),
                new HttpHeaders());
    }
}
