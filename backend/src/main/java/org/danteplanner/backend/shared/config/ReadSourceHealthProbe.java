package org.danteplanner.backend.shared.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.scheduling.annotation.Scheduled;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReadSourceHealthProbe {

    private static final int MIN_VALID_TIMEOUT_SECONDS = 1;

    public record Target(String name, DataSource direct, CircuitBreaker breaker) {
    }

    private final List<SourceProbe> probes;
    private final int failureThreshold;
    private final int validTimeoutSeconds;

    public ReadSourceHealthProbe(List<Target> targets, ReadSourceProperties.Probe settings) {
        this.probes = targets.stream().map(SourceProbe::new).toList();
        this.failureThreshold = settings.getFailureThreshold();
        this.validTimeoutSeconds = (int) Math.max(MIN_VALID_TIMEOUT_SECONDS, settings.getTimeout().toSeconds());
    }

    @Scheduled(
            fixedDelayString = "${" + ReadSourceProperties.PROBE_INTERVAL_PROPERTY + ":"
                    + ReadSourceProperties.PROBE_INTERVAL_TEXT + "}",
            scheduler = ReadSourcesConfig.PROBE_SCHEDULER)
    public void probe() {
        probes.forEach(SourceProbe::probe);
    }

    private final class SourceProbe {

        private final Target target;
        private int consecutiveFailures;

        private SourceProbe(Target target) {
            this.target = target;
        }

        private void probe() {
            CircuitBreaker breaker = target.breaker();
            if (answers()) {
                consecutiveFailures = 0;
                if (breaker.getState() != CircuitBreaker.State.CLOSED) {
                    breaker.transitionToClosedState();
                    log.info("Read source {} answered its probe; breaker {} closed", target.name(), breaker.getName());
                }
                return;
            }
            consecutiveFailures++;
            if (consecutiveFailures >= failureThreshold && breaker.getState() != CircuitBreaker.State.OPEN) {
                breaker.transitionToOpenState();
                log.info("Read source {} failed {} consecutive probes; breaker {} opened",
                        target.name(), consecutiveFailures, breaker.getName());
            }
        }

        private boolean answers() {
            try (Connection connection = target.direct().getConnection()) {
                return connection.isValid(validTimeoutSeconds);
            } catch (SQLException e) {
                return false;
            }
        }
    }
}
