package org.danteplanner.backend.shared.outbox.config;

/**
 * Environment-invariant constants for the domain event outbox.
 */
public final class OutboxConstants {

    /**
     * Dispatch attempts a row gets before the relay stops picking it up.
     *
     * <p>High enough that a Redis or database outage spanning several relay ticks does not strand a
     * healthy row, low enough that a genuinely undispatchable one stops consuming a batch slot on
     * every pass.
     */
    public static final int DISPATCH_ATTEMPT_CAP = 10;

    private OutboxConstants() {
    }
}
