package org.danteplanner.backend.integration.consistency;

@FunctionalInterface
public interface OperationPerformer {

    void perform(PlannedOperation operation) throws Exception;
}
