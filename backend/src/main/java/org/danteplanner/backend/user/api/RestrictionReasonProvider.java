package org.danteplanner.backend.user.api;

import java.util.Optional;
import java.util.UUID;

public interface RestrictionReasonProvider {

    Optional<String> latestBanReason(UUID targetPublicId);

    Optional<String> latestTimeoutReason(UUID targetPublicId);
}
