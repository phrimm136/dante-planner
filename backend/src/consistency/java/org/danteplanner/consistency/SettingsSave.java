package org.danteplanner.consistency;

public record SettingsSave(String session, boolean value, long invokedAt, long completedAt, VisibilityResult result)
        implements Operation {
}
