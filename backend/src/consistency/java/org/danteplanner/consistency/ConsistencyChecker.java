package org.danteplanner.consistency;

import java.util.List;

public final class ConsistencyChecker {

    private static final List<Rule> RULES = List.of(
            new AuthorReadYourWrites(),
            new ReadAfterDelete(),
            new ExposureDuringDelete(),
            new MonotonicReadRegression(),
            new ReadAfterUnpublish(),
            new ReadAfterPublish(),
            new ReadAfterCreate());

    private ConsistencyChecker() {
    }

    public static Verdict check(History history) {
        List<Verdict> verdicts = RULES.stream().map(rule -> rule.judge(history)).toList();
        return new Verdict(
                verdicts.stream().flatMap(verdict -> verdict.violations().stream()).toList(),
                verdicts.stream().flatMap(verdict -> verdict.anomalies().stream()).toList());
    }
}
