package org.danteplanner.backend.user.api;

import java.util.UUID;

import org.danteplanner.backend.shared.failure.Option;

public interface RestrictionReasonProvider {

    Option<String> latestBanReason(UUID targetPublicId);

    Option<String> latestTimeoutReason(UUID targetPublicId);
}
