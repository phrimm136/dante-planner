package org.danteplanner.backend.integration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.danteplanner.backend.shared.freshness.MarkerStore;
import org.danteplanner.backend.support.Hang;

import static org.assertj.core.api.Assertions.assertThat;

final class MarkerSourceBreakers {

    private static final List<String> NAMES = List.of(
            MarkerStore.BREAKER_NAME_PREFIX + MarkerStore.LOCAL_SOURCE,
            MarkerStore.BREAKER_NAME_PREFIX + MarkerStore.AUTH_SOURCE);

    private MarkerSourceBreakers() {
    }

    static void awaitClosed(CircuitBreakerRegistry registry) {
        Hang.until(() -> allIn(registry, CircuitBreaker.State.CLOSED),
                "the marker probe closing both marker-source breakers after the auth Redis cut");
    }

    static void awaitOpen(CircuitBreakerRegistry registry) {
        Hang.until(() -> allIn(registry, CircuitBreaker.State.OPEN),
                "the marker probe opening both marker-source breakers during the auth Redis cut");
    }

    static void assertClosed(CircuitBreakerRegistry registry, String because) {
        assertThat(states(registry))
                .as(because)
                .containsExactlyInAnyOrderEntriesOf(NAMES.stream()
                        .collect(Collectors.toMap(name -> name, name -> CircuitBreaker.State.CLOSED)));
    }

    private static boolean allIn(CircuitBreakerRegistry registry, CircuitBreaker.State state) {
        return states(registry).values().stream().allMatch(state::equals);
    }

    private static Map<String, CircuitBreaker.State> states(CircuitBreakerRegistry registry) {
        return NAMES.stream().collect(Collectors.toMap(name -> name,
                name -> registry.circuitBreaker(name).getState()));
    }
}
