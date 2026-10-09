package org.danteplanner.backend.integration.consistency;

public record StaleWindow(long stoppedAt, long restartedAt) {
}
