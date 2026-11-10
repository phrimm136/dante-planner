package org.danteplanner.freshnessfixture;

import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.ScopedRead;

public class UnannotatedInterfaceScopedReadFixture implements UnannotatedInterfaceScopedRead {

    @Override
    @Transactional(readOnly = true)
    public ScopedRead<String> scopedByTheImplementation() {
        return new ScopedRead<>("reached", Set.of());
    }
}
