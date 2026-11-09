package org.danteplanner.freshnessfixture;

import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.ScopedRead;

@Transactional(readOnly = true)
public class ClassLevelScopedReadFixture {

    public ScopedRead<String> scopedByTheClass() {
        return new ScopedRead<>("reached", Set.of());
    }
}
