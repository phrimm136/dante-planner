package org.danteplanner.consistency;

public sealed interface ThreadResult permits Shown, NotFound, ReadFailed {
}
