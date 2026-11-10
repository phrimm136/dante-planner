package org.danteplanner.backend.shared.freshness;

public class NoDispatchStamp implements DispatchStamp {

    @Override
    public void around(Runnable dispatch) {
        dispatch.run();
    }

    @Override
    public void raise() {
    }
}
