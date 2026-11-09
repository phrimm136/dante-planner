package org.danteplanner.freshnessfixture;

import java.util.Set;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.FreshnessExempt;
import org.danteplanner.backend.shared.freshness.FreshnessPending;
import org.danteplanner.backend.shared.freshness.ScopedRead;

public class DoublyDeclaredReadFixture {

    @Transactional(readOnly = true)
    @FreshnessExempt(reason = "runs with no request bound")
    public ScopedRead<String> scopedAndExempt() {
        return new ScopedRead<>("reached", Set.of());
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    @FreshnessPending(reason = "its scope arrives in M2")
    public String innerAndPending() {
        return "reached";
    }
}
