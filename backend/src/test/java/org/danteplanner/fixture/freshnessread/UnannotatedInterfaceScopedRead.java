package org.danteplanner.fixture.freshnessread;

import org.danteplanner.backend.shared.freshness.ScopedRead;

public interface UnannotatedInterfaceScopedRead {

    ScopedRead<String> scopedByTheImplementation();
}
