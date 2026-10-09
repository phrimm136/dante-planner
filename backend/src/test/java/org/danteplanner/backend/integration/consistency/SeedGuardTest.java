package org.danteplanner.backend.integration.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.danteplanner.consistency.Applied;
import org.danteplanner.consistency.Changed;
import org.danteplanner.consistency.Delete;
import org.danteplanner.consistency.Deleted;
import org.danteplanner.consistency.Found;
import org.danteplanner.consistency.ListRead;
import org.danteplanner.consistency.NotFound;
import org.danteplanner.consistency.Operation;
import org.danteplanner.consistency.PublicRead;
import org.danteplanner.consistency.Publish;
import org.danteplanner.consistency.Read;
import org.danteplanner.consistency.ReadFailed;
import org.danteplanner.consistency.Rejected;
import org.danteplanner.consistency.Save;
import org.danteplanner.consistency.Unknown;
import org.danteplanner.consistency.Unpublish;

import static org.assertj.core.api.Assertions.assertThat;

class SeedGuardTest {

    private static final String K1 = "k1";

    @Test
    void guard_WhenEveryKindHasAKnownOutcome_Passes() {
        List<Operation> operations = List.of(
                new Save("s1", K1, 1, 2, new Applied(1)),
                new Read("s2", K1, 3, 4, new Found(1)),
                new ListRead("s3", 5, 6, List.of(K1)),
                new Publish("s1", K1, 7, 8, new Changed()),
                new PublicRead("anon", K1, 9, 10, new Found(1)),
                new Unpublish("s2", K1, 11, 12, new Rejected(409)),
                new Delete("s1", K1, 13, 14, new Deleted()),
                new Read("s3", K1, 15, 16, new ReadFailed(429)),
                new PublicRead("anon", K1, 17, 18, new NotFound()));

        assertThat(SeedGuard.failures(operations)).isEmpty();
    }

    @Test
    void guard_WhenEachOutcomeBearingKindIsUnknown_CountsAllSix() {
        List<Operation> operations = List.of(
                new Save("s1", K1, 1, 2, new Unknown()),
                new Delete("s1", K1, 3, 4, new Unknown()),
                new Read("s2", K1, 5, 6, new ReadFailed(0)),
                new ListRead("s3", 7, 8, List.of()),
                new Publish("s1", K1, 9, 10, new Unknown()),
                new Unpublish("s2", K1, 11, 12, new Unknown()),
                new PublicRead("anon", K1, 13, 14, new ReadFailed(0)));

        assertThat(SeedGuard.failures(operations))
                .containsExactly("6 operations have an unknown outcome, and the run injects no client-side fault");
    }
}
