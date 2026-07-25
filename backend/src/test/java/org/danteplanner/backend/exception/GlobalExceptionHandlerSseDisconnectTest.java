package org.danteplanner.backend.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.Sentry;
import org.apache.catalina.connector.ClientAbortException;
import org.danteplanner.backend.shared.exception.GlobalExceptionHandler;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * A client that walks away from an SSE stream is expected traffic, not an incident: the write
 * failure is swallowed at DEBUG and never reaches Sentry. Any other IOException is a real failure
 * and must raise an alert.
 *
 * <p>The container signals the abort by type, while the JVM signals a socket teardown only through
 * the OS strerror text on a plain IOException, so both arms have to hold.</p>
 */
class GlobalExceptionHandlerSseDisconnectTest {

    @Test
    void a_client_abort_without_a_message_raises_no_alert() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handle(new ClientAbortException());

            sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
        }
    }

    @Test
    void a_socket_teardown_strerror_raises_no_alert() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handle(new IOException("Broken pipe"));

            sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
        }
    }

    @Test
    void an_unrelated_io_failure_raises_an_alert() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handle(new IOException("No space left on device"));

            sentry.verify(() -> Sentry.captureException(any(Throwable.class)));
        }
    }

    private static void handle(IOException ex) {
        new GlobalExceptionHandler(mock(CookieUtils.class), new ObjectMapper()).handleIOException(ex);
    }
}
