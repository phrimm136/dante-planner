package org.danteplanner.consistency;

public record Anomaly(String kind, String session, String key, Operation operation) {
}
