package org.danteplanner.backend.moderation.api;

import java.util.UUID;

public record ModeratedComment(Long id, UUID publicId, State state) {

    public enum State {
        LIVE,
        DELETED
    }
}
