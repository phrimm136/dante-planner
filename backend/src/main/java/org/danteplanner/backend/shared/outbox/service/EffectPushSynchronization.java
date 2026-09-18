package org.danteplanner.backend.shared.outbox.service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronization;

@RequiredArgsConstructor
public class EffectPushSynchronization implements TransactionSynchronization {

    private final EffectPushQueue pushes;

    @Override
    public void afterCommit() {
        pushes.flush();
    }
}
