package org.danteplanner.consistency;

public record SettingsRead(String session, long invokedAt, long completedAt, SettingsResult result)
        implements Operation {
}
