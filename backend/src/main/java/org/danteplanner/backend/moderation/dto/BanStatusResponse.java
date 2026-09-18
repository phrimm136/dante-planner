package org.danteplanner.backend.moderation.dto;

public record BanStatusResponse(boolean banned, String message) {
}
