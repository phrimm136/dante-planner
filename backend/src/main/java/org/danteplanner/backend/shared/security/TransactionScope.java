package org.danteplanner.backend.shared.security;

public enum TransactionScope {
    READ_ONLY,
    IN_TRANSACTION,
    NONE;

    static TransactionScope of(boolean readOnly, boolean active) {
        if (readOnly) {
            return READ_ONLY;
        }
        return active ? IN_TRANSACTION : NONE;
    }
}
