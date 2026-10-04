package org.danteplanner.backend.shared.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.scheduling.annotation.Scheduled;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrimaryHealthProbe {

    private static final int MIN_VALID_TIMEOUT_SECONDS = 1;

    private final DataSource primaryDirect;
    private final CircuitBreaker breaker;
    private final int failureThreshold;
    private final int validTimeoutSeconds;

    private int consecutiveFailures;

    public PrimaryHealthProbe(
            DataSource primaryDirect, CircuitBreaker breaker, FallbackReplicaProperties.Probe settings) {
        this.primaryDirect = primaryDirect;
        this.breaker = breaker;
        this.failureThreshold = settings.getFailureThreshold();
        this.validTimeoutSeconds = (int) Math.max(MIN_VALID_TIMEOUT_SECONDS, settings.getTimeout().toSeconds());
    }

    @Scheduled(
            fixedDelayString = "${" + FallbackReplicaProperties.PROBE_INTERVAL_PROPERTY + ":"
                    + FallbackReplicaProperties.PROBE_INTERVAL_TEXT + "}",
            scheduler = ReadFallbackConfig.PRIMARY_PROBE_SCHEDULER)
    public void probe() {
        if (primaryAnswers()) {
            consecutiveFailures = 0;
            if (breaker.getState() != CircuitBreaker.State.CLOSED) {
                breaker.transitionToClosedState();
                log.info("Primary probe succeeded; primary-read breaker closed");
            }
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold && breaker.getState() != CircuitBreaker.State.OPEN) {
            breaker.transitionToOpenState();
            log.warn("Primary probe failed {} consecutive times; primary-read breaker opened", consecutiveFailures);
        }
    }

    private boolean primaryAnswers() {
        try (Connection connection = primaryDirect.getConnection()) {
            return connection.isValid(validTimeoutSeconds);
        } catch (SQLException e) {
            return false;
        }
    }
}
