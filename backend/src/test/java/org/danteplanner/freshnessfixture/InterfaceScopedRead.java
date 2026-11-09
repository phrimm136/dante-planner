package org.danteplanner.freshnessfixture;

import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.ScopedRead;

public interface InterfaceScopedRead {

    @Transactional(readOnly = true)
    ScopedRead<String> scopedByTheInterface();
}
