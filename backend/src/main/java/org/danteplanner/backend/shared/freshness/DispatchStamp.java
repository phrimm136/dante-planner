package org.danteplanner.backend.shared.freshness;

public interface DispatchStamp {

    void around(Runnable dispatch);

    void raise();
}
