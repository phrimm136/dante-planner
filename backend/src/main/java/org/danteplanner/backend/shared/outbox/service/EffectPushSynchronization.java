package org.danteplanner.backend.shared.outbox.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.freshness.DispatchStamp;
import org.springframework.transaction.support.TransactionSynchronization;

@RequiredArgsConstructor
public class EffectPushSynchronization implements TransactionSynchronization {

    private final DispatchStamp stamp;
    private final EffectPushQueue pushes;

    @Override
    public void afterCommit() {
        try {
            stamp.raise();
        } finally {
            pushes.flush();
        }
    }
}
