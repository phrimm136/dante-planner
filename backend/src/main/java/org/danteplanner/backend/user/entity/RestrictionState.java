package org.danteplanner.backend.user.entity;

/**
 * The restriction in force on an account at a given instant.
 *
 * <p>A live timeout is reported ahead of a ban, so {@link #BANNED} names an account that is banned
 * and not also serving a timeout. {@link User#isBanned()} answers the ban question on its own.</p>
 */
public enum RestrictionState {

    /** No restriction in force. */
    ACTIVE,

    /** A timeout is running: publishing, commenting, replying, and editing a comment are withdrawn. */
    TIMED_OUT,

    /** The account is banned: everything a timeout withdraws, plus voting and reporting. */
    BANNED
}
