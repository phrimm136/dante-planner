package org.danteplanner.backend.user.api;

public interface AccountLifecycleParticipant {

    default void accountDeactivated(Long userId) {
    }

    default void accountReactivated(Long userId) {
    }

    default void reassignToSentinel(Long userId, Long sentinelUserId) {
    }

    default void deleteOwnedData(Long userId) {
    }
}
