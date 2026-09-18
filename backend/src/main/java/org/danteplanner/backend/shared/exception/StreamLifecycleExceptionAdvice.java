package org.danteplanner.backend.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

/**
 * Spring dispatches exceptions to an advice after an SseEmitter has committed the response.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class StreamLifecycleExceptionAdvice {

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void timedOut(AsyncRequestTimeoutException ex) {
        log.debug("SSE connection timeout: {}", ex.getMessage());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void notUsable(AsyncRequestNotUsableException ex) {
        log.debug("SSE connection not usable: {}", ex.getMessage());
    }
}
