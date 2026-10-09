package org.danteplanner.consistency;

import java.util.List;

public record Verdict(List<Violation> violations, List<Anomaly> anomalies) {

    public Verdict {
        violations = List.copyOf(violations);
        anomalies = List.copyOf(anomalies);
    }
}
