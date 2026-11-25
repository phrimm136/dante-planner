package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.entity.Listing;

import java.time.Instant;

public sealed interface ModeratorAction {

    sealed interface Withdrawal extends ModeratorAction {
    }

    sealed interface Relisting extends ModeratorAction {

        default Listing listingAt(Instant now) {
            return switch (this) {
                case Hide(Long moderatorId, String reason) -> new Listing.Hidden(moderatorId, reason, now);
                case Unhide unhide -> new Listing.Listed();
            };
        }
    }

    record TakeDown() implements Withdrawal {
    }

    record Unpublish() implements Withdrawal {
    }

    record Hide(Long moderatorId, String reason) implements Relisting {
    }

    record Unhide() implements Relisting {
    }
}
