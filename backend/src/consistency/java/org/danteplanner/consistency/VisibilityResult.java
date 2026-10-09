package org.danteplanner.consistency;

public sealed interface VisibilityResult permits Changed, Rejected, Unknown {
}
