package org.danteplanner.backend.exception;
import org.danteplanner.backend.shared.exception.GlobalExceptionHandler;

import org.danteplanner.backend.shared.util.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Verifies that a DB outage on the controller/service path is mapped to 503 by
 * {@link GlobalExceptionHandler}, through real {@code @ExceptionHandler} dispatch.
 *
 * <p>When the DB is down, the failure surfaces as one of two unrelated hierarchies depending on
 * where the connection was needed: a non-transactional query yields
 * {@link DataAccessResourceFailureException}; a {@code @Transactional} method fails at
 * transaction-begin and yields {@link CannotCreateTransactionException} (a TransactionException,
 * NOT a DataAccessException). The latter was the path that escaped to a catch-all 500 in
 * production. Using {@code standaloneSetup} + {@code setControllerAdvice} exercises Spring's
 * exception dispatch — a direct call to the handler method would prove the body but not that the
 * {@code @ExceptionHandler} annotation actually routes both exception types here.</p>
 */
class GlobalExceptionHandlerDbUnavailableTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/boom/transaction")
        public String transactionBegin() {
            throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction");
        }

        @GetMapping("/boom/query")
        public String queryTime() {
            throw new DataAccessResourceFailureException("Unable to acquire JDBC connection");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(CookieUtils.class)))
                .build();
    }

    @Test
    @DisplayName("@Transactional path (CannotCreateTransactionException) maps to 503, not 500")
    void transactionBeginFailure_WhenThrown_MapsTo503() throws Exception {
        mockMvc.perform(get("/boom/transaction"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Query-time path (DataAccessResourceFailureException) still maps to 503")
    void queryTimeFailure_WhenThrown_MapsTo503() throws Exception {
        mockMvc.perform(get("/boom/query"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"));
    }
}
