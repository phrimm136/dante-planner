package org.danteplanner.backend.shared.outbox.service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * Releases a dispatch's pushes once its transaction is durable.
 *
 * <p>A named class rather than a lambda: this is the seam that keeps every publish out of the
 * dispatch's own call graph, which is the shape {@code EffectPlacementTest} reads.</p>
 */
@RequiredArgsConstructor
public class EffectPushSynchronization implements TransactionSynchronization {

    private final EffectPushQueue pushes;

    @Override
    public void afterCommit() {
        pushes.flush();
    }
}
