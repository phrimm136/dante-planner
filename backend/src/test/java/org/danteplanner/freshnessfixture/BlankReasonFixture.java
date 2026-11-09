package org.danteplanner.freshnessfixture;

import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.shared.freshness.FreshnessExempt;
import org.danteplanner.backend.shared.freshness.FreshnessPending;

public class BlankReasonFixture {

    @Transactional(readOnly = true)
    @FreshnessExempt(reason = " ")
    public String blankExempt() {
        return "reached";
    }

    @Transactional(readOnly = true)
    @FreshnessPending(reason = "")
    public String blankPending() {
        return "reached";
    }
}
