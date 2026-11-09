package org.danteplanner.freshnessfixture;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.FreshnessExempt;
import org.danteplanner.backend.shared.freshness.FreshnessPending;

@Transactional(readOnly = true)
public class ClassLevelDeclaredFixture {

    @FreshnessPending(reason = "its scope arrives in M2")
    public String pending() {
        return "reached";
    }

    @FreshnessExempt(reason = "runs with no request bound")
    public String exempt() {
        return "reached";
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public String inner() {
        return "reached";
    }
}
