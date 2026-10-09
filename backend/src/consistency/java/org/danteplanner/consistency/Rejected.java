package org.danteplanner.consistency;

public record Rejected(int status) implements SaveResult, DeleteResult, VisibilityResult {
}
