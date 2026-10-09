package org.danteplanner.backend.integration.consistency;

public sealed interface PlannedOperation {

    record Save(Slot slot) implements PlannedOperation {
    }

    record Read(Slot slot) implements PlannedOperation {
    }

    record ListAll() implements PlannedOperation {
    }

    record Delete(Slot slot) implements PlannedOperation {
    }
}
