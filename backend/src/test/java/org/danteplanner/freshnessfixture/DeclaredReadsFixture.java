package org.danteplanner.freshnessfixture;

import java.util.Set;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.FreshnessExempt;
import org.danteplanner.backend.shared.freshness.FreshnessPending;
import org.danteplanner.backend.shared.freshness.ScopedRead;

public class DeclaredReadsFixture {

    @Transactional(readOnly = true)
    public ScopedRead<String> scoped() {
        return new ScopedRead<>("reached", Set.of());
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public String inner() {
        return "reached";
    }

    @Transactional(readOnly = true)
    @FreshnessPending(reason = "its scope arrives in M2")
    public String pending() {
        return "reached";
    }

    @Transactional(readOnly = true)
    @FreshnessExempt(reason = "runs with no request bound")
    public String exempt() {
        return "reached";
    }

    @Transactional
    public String write() {
        return "reached";
    }
}
