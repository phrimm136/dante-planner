package org.danteplanner.backend.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.danteplanner.backend.shared.exception.GlobalExceptionHandler;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * An IOException that is not a client walking away is a server failure, and the client must be
 * told so: an uncommitted response carries the standard error envelope under 500, while a
 * committed one can only be marked handled.
 */
class GlobalExceptionHandlerIoFailureTest {

    private MockMvc mockMvc;

    @RestController
    static class IoThrowingController {
        @GetMapping(value = "/io/boom", produces = MediaType.APPLICATION_JSON_VALUE)
        public String boom() throws IOException {
            throw new IOException("No space left on device");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new IoThrowingController())
                .setControllerAdvice(handler())
                .build();
    }

    @Test
    @DisplayName("An unexpected IOException before commit answers 500 with the error envelope")
    void unexpectedIoFailure_WhenResponseNotCommitted_AnswersFiveHundredWithEnvelope() throws Exception {
        mockMvc.perform(get("/io/boom").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("An unexpected IOException after commit writes nothing")
    void unexpectedIoFailure_WhenResponseCommitted_WritesNothing() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(true);

        assertThat(handler().handleIOException(new IOException("No space left on device"), response)).isNull();
        verify(response, never()).getWriter();
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("A client disconnect is handled without a response body")
    void clientDisconnect_WhenSeen_IsHandledWithoutABody() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThat(handler().handleIOException(new ClientAbortException(), response)).isNull();
        verify(response, never()).isCommitted();
    }

    private static GlobalExceptionHandler handler() {
        return new GlobalExceptionHandler(mock(CookieUtils.class), new ObjectMapper());
    }
}
