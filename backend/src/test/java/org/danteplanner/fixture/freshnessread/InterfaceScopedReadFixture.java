package org.danteplanner.fixture.freshnessread;

import java.util.Set;

import org.danteplanner.backend.shared.freshness.ScopedRead;

public class InterfaceScopedReadFixture implements InterfaceScopedRead {

    @Override
    public ScopedRead<String> scopedByTheInterface() {
        return new ScopedRead<>("reached", Set.of());
    }
}
