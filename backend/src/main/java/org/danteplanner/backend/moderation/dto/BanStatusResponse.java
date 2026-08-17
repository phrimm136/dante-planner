package org.danteplanner.backend.moderation.dto;

/**
 * Response DTO for an administrator banning or unbanning an account.
 *
 * @param banned  the account's ban state after the action
 * @param message a human-readable message describing the operation result
 */
public record BanStatusResponse(boolean banned, String message) {
}
