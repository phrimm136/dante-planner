package org.danteplanner.backend.moderation.dto;

import org.danteplanner.backend.shared.restriction.Restriction;

public record BanStatusResponse(boolean banned, String message) {

    public static BanStatusResponse of(Restriction restriction, String message) {
        return switch (restriction) {
            case Restriction.Banned banned -> new BanStatusResponse(true, message);
            case Restriction.TimedOut timedOut -> new BanStatusResponse(false, message);
            case Restriction.Active active -> new BanStatusResponse(false, message);
        };
    }
}
