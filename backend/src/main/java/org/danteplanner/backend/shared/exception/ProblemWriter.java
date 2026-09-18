package org.danteplanner.backend.shared.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Writes a problem document from outside the DispatcherServlet, where no message converter runs.
 */
@Component
@RequiredArgsConstructor
public class ProblemWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail body,
            HttpHeaders headers) throws IOException {
        body.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(body.getStatus());
        headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
