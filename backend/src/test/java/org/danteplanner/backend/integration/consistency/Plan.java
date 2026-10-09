package org.danteplanner.backend.integration.consistency;

import java.util.List;

public record Plan(Phase phaseA, Phase phaseB) {

    public record Phase(List<Round> rounds, FaultWindow fault) {

        public Phase {
            rounds = List.copyOf(rounds);
        }
    }

    public record Round(List<List<PlannedOperation>> bySession) {

        public Round {
            bySession = bySession.stream().map(List::copyOf).toList();
        }
    }

    public record FaultWindow(int stopAt, int startAt) {
    }
}
