package org.danteplanner.freshnessfixture;

import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.ScopedRead;

public class PrivateHelperFixture {

    @Transactional(readOnly = true)
    public ScopedRead<String> scoped() {
        return helper();
    }

    private ScopedRead<String> helper() {
        return new ScopedRead<>("reached", Set.of());
    }

    @Transactional(readOnly = true)
    private String privateReadOnly() {
        return "reached";
    }
}
