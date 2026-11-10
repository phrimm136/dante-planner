package org.danteplanner.freshnessfixture;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.FreshnessExempt;

@Transactional(readOnly = true)
public class ClassLevelDeclaredFixture {

    @FreshnessExempt(reason = "runs with no request bound")
    public String exempt() {
        return "reached";
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public String inner() {
        return "reached";
    }
}
