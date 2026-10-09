package org.danteplanner.consistency;

public record Applied(long syncVersion) implements SaveResult {
}
