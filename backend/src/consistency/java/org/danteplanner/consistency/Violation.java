package org.danteplanner.consistency;

public record Violation(String rule, String session, String key, Operation operation) {
}
