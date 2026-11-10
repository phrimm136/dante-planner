package org.danteplanner.freshnessfixture;

import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.FreshnessExempt;

public class BlankReasonFixture {

    @Transactional(readOnly = true)
    @FreshnessExempt(reason = " ")
    public String blankExempt() {
        return "reached";
    }

    @Transactional(readOnly = true)
    @FreshnessExempt(reason = "")
    public String emptyExempt() {
        return "reached";
    }
}
