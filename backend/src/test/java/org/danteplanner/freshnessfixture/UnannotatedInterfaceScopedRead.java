package org.danteplanner.freshnessfixture;

import org.danteplanner.backend.shared.freshness.ScopedRead;

public interface UnannotatedInterfaceScopedRead {

    ScopedRead<String> scopedByTheImplementation();
}
