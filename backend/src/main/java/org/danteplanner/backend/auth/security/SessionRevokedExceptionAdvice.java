package org.danteplanner.backend.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SessionRevokedExceptionAdvice {

    private final CookieUtils cookieUtils;
    private final ApiExceptionHandler apiExceptionHandler;

    @ExceptionHandler(SessionRevokedException.class)
    public ResponseEntity<Object> handleSessionRevoked(
            SessionRevokedException ex, HttpServletResponse response, WebRequest request) {
        cookieUtils.clearAuthCookies(response);
        return apiExceptionHandler.respond(ex, ex.getBody(), ex.getHeaders(), request);
    }
}
