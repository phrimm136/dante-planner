package org.danteplanner.consistency;

public sealed interface ReadResult permits Found, NotFound, ReadFailed {
}
