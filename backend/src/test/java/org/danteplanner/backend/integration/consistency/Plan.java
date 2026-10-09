package org.danteplanner.backend.integration.consistency;

import java.util.List;

public record Plan(Phase phaseA, Phase phaseB) {

    public record Phase(List<Round> rounds, Fault fault) {

        public Phase {
            rounds = List.copyOf(rounds);
        }
    }

    public record Round(List<List<PlannedOperation>> bySession) {

        public Round {
            bySession = bySession.stream().map(List::copyOf).toList();
        }
    }

    public sealed interface Fault {
    }

    public record Latency(long millis) implements Fault {
    }

    public record Stopped() implements Fault {
    }
}
