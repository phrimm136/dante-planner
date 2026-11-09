package org.danteplanner.backend.integration.consistency;

public sealed interface PlannedOperation {

    record Save(KeyDraw key) implements PlannedOperation {
    }

    record Create() implements PlannedOperation {
    }

    record Read(KeyDraw key) implements PlannedOperation {
    }

    record ListAll() implements PlannedOperation {
    }

    record Publish(KeyDraw key) implements PlannedOperation {
    }

    record Unpublish(KeyDraw key) implements PlannedOperation {
    }

    record PublicRead(KeyDraw key) implements PlannedOperation {
    }

    record Delete(KeyDraw key) implements PlannedOperation {
    }

    record CatalogList() implements PlannedOperation {
    }

    record ThreadRead(KeyDraw key) implements PlannedOperation {
    }

    record Deactivate() implements PlannedOperation {
    }

    record Reactivate() implements PlannedOperation {
    }
}
