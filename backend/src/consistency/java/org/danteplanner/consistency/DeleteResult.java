package org.danteplanner.consistency;

public sealed interface DeleteResult permits Deleted, Rejected, Unknown {
}
