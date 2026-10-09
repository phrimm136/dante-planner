package org.danteplanner.consistency;

public sealed interface SaveResult permits Applied, Rejected, Unknown {
}
