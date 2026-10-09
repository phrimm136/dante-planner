package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.danteplanner.consistency.Applied;
import org.danteplanner.consistency.Delete;
import org.danteplanner.consistency.Deleted;
import org.danteplanner.consistency.Found;
import org.danteplanner.consistency.NotFound;
import org.danteplanner.consistency.Operation;
import org.danteplanner.consistency.Read;
import org.danteplanner.consistency.ReadFailed;
import org.danteplanner.consistency.Save;

import static org.assertj.core.api.Assertions.assertThat;

class SeedGuardTest {

    private static final StaleWindow PHASE_A_WINDOW = new StaleWindow(100, 200);
    private static final String K1 = "k1";
    private static final String K2 = "k2";

    private static final List<Operation> SETUP_AND_PHASE_B = List.of(
            new Save("s1", K1, 1, 2, new Applied(1)),
            new Save("s1", K2, 3, 4, new Applied(1)),
            new Read("s2", K2, 150, 160, new Found(1)),
            new Delete("s1", K1, 300, 310, new Deleted()),
            new Read("s1", K1, 320, 330, new NotFound()),
            new Read("s1", K2, 340, 350, new Found(1)));

    @Test
    void guard_WhenTheOnlyAuthorPairsAreSetupSavesAndPhaseBReads_FailsTheSeed() {
        assertThat(SeedGuard.failures(SETUP_AND_PHASE_B, PHASE_A_WINDOW))
                .singleElement().asString().startsWith("s1 has no Found or NotFound read");
    }

    @Test
    void guard_WhenOneAuthorPairFallsInsideTheStaleWindow_Passes() {
        List<Operation> operations = new ArrayList<>(SETUP_AND_PHASE_B);
        operations.add(new Save("s1", K2, 110, 120, new Applied(2)));
        operations.add(new Read("s1", K2, 130, 140, new Found(2)));

        assertThat(SeedGuard.failures(operations, PHASE_A_WINDOW)).isEmpty();
    }

    @Test
    void guard_WhenTheAuthorReadCompletesAfterTheRestart_FailsTheSeed() {
        List<Operation> operations = new ArrayList<>(SETUP_AND_PHASE_B);
        operations.add(new Save("s1", K2, 110, 120, new Applied(2)));
        operations.add(new Read("s1", K2, 190, 210, new Found(2)));

        assertThat(SeedGuard.failures(operations, PHASE_A_WINDOW))
                .singleElement().asString().startsWith("s1 has no Found or NotFound read");
    }

    @Test
    void guard_WhenAReadHasNoResponse_FailsTheSeedForTheUnknownOutcome() {
        List<Operation> operations = new ArrayList<>(SETUP_AND_PHASE_B);
        operations.add(new Save("s1", K2, 110, 120, new Applied(2)));
        operations.add(new Read("s1", K2, 130, 140, new Found(2)));
        operations.add(new Read("s3", K2, 150, 160, new ReadFailed(0)));

        assertThat(SeedGuard.failures(operations, PHASE_A_WINDOW))
                .containsExactly("1 operations have an unknown outcome, and phase 1 injects no client-side fault");
    }
}
