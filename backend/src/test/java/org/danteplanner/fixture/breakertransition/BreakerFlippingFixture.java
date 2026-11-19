package org.danteplanner.fixture.breakertransition;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

public class BreakerFlippingFixture {

    void open(CircuitBreaker breaker) {
        breaker.transitionToOpenState();
    }

    void close(CircuitBreaker breaker) {
        breaker.transitionToClosedState();
    }
}
