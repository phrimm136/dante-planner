package org.danteplanner.backend.integration.consistency;

@FunctionalInterface
public interface AccountControl {

    void reactivate() throws Exception;
}
