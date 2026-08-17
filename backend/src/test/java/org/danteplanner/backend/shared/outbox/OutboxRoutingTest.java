package org.danteplanner.backend.shared.outbox;

import org.danteplanner.backend.shared.outbox.service.DomainEventAttemptRecorder;
import org.danteplanner.backend.shared.outbox.service.DomainEventDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two transaction declarations the outbox cannot be refactored out of.
 *
 * <p>Neither is visible in behaviour a functional test could catch. An untransacted scan inherits
 * {@code SimpleJpaRepository}'s {@code readOnly = true}, which is this tree's replica-routing
 * signal, and the recovery scan would then ask a lagging replica which rows still need recovering —
 * silently concluding there are none. An attempt counter written in the dispatch transaction is
 * rolled back by the very failure it records, so a permanently failing row is retried forever with
 * its counter at zero.</p>
 */
class OutboxRoutingTest {

    @Test
    @DisplayName("the relay's scan is declared read-write, so it reads the primary")
    void pendingEventIds_WhenDeclared_IsTransactionalAndNotReadOnly() throws NoSuchMethodException {
        Method scan = DomainEventDispatcher.class.getMethod(
                "pendingEventIds", Instant.class, int.class);

        Transactional declared = scan.getAnnotation(Transactional.class);

        assertThat(declared)
                .as("without a transaction the scan inherits the repository's readOnly=true and "
                        + "routes to the replica, where an unreplicated row reads as nothing to do")
                .isNotNull();
        assertThat(declared.readOnly())
                .as("readOnly is the replica-routing signal, not an optimization")
                .isFalse();
    }

    @Test
    @DisplayName("the attempt counter is written in a transaction of its own")
    void recordAttempt_WhenDeclared_RunsRequiresNew() throws NoSuchMethodException {
        Method recordAttempt = DomainEventAttemptRecorder.class.getMethod(
                "recordAttempt", long.class);

        Transactional declared = recordAttempt.getAnnotation(Transactional.class);

        assertThat(declared).isNotNull();
        assertThat(declared.propagation())
                .as("joined to the dispatch, the increment is undone by the failure it exists to "
                        + "record, and the relay never stops retrying the row")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
