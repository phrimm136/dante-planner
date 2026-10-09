package org.danteplanner.consistency;

public record ReadFailed(int status) implements ReadResult {
}
