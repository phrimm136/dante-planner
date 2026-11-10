package org.danteplanner.backend.architecture;

import com.tngtech.archunit.lang.ArchRule;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.danteplanner.backend.shared.config.ReadSourceHealthProbe;
import org.danteplanner.backend.shared.redis.RedisRoleProbe;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

final class BreakerTransitionRule {

    static final ArchRule ONLY_PROBES_MOVE_BREAKERS =
            noClasses()
                    .that().doNotBelongToAnyOf(RedisRoleProbe.class, ReadSourceHealthProbe.class)
                    .should().callMethod(CircuitBreaker.class, "transitionToOpenState")
                    .orShould().callMethod(CircuitBreaker.class, "transitionToClosedState")
                    .as("only a health probe opens or closes a circuit breaker: RedisRoleProbe the Redis role"
                            + " breakers, ReadSourceHealthProbe the read-source breakers");

    private BreakerTransitionRule() {
    }
}
