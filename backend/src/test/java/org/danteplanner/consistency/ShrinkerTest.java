package org.danteplanner.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShrinkerTest {

    private static final int HISTORY_SIZE = 200;
    private static final int FILLER_KEYS = 9;
    private static final int SESSIONS = 3;

    @Test
    void shrink_WhenOneReadAfterDeleteHidesInTwoHundredOperations_KeepsOnlyTheDeleteAndTheRead() {
        Delete delete = new Delete("s1", "p9", 1000, 1010, new Deleted());
        Read resurrected = new Read("s2", "p9", 1020, 1030, new Found(2));
        Violation target = new Violation("read-after-delete", "s2", "p9", resurrected);
        History history = History.of(fillerAround(delete, resurrected, new Random(7)));
        assertThat(history.operations()).hasSize(HISTORY_SIZE);
        assertThat(ConsistencyChecker.check(history).violations()).containsExactly(target);

        History shrunk = Shrinker.shrink(history, target);

        assertThat(shrunk.operations()).containsExactly(delete, resurrected);
        assertThat(ConsistencyChecker.check(shrunk).violations()).containsExactly(target);
    }

    @Test
    void shrink_WhenTheReadPrecedesTheDeleteInTheHistory_KeepsTheirOriginalOrder() {
        Delete delete = new Delete("s1", "p1", 10, 20, new Deleted());
        Read resurrected = new Read("s2", "p1", 25, 30, new Found(3));
        Violation target = new Violation("read-after-delete", "s2", "p1", resurrected);

        History shrunk = Shrinker.shrink(History.of(List.of(
                resurrected,
                new Save("s3", "p2", 1, 2, new Applied(1)),
                delete,
                new Read("s3", "p2", 40, 50, new Found(1)))), target);

        assertThat(shrunk.operations()).containsExactly(resurrected, delete);
    }

    @Test
    void shrink_WhenRemovingAnOperationWouldRaiseTheTarget_ReturnsTheHistoryUnchanged() {
        Read read = new Read("s2", "p1", 25, 30, new Found(3));
        History clean = History.of(List.of(
                new Save("s3", "p1", 5, 15, new Applied(3)),
                new Delete("s1", "p1", 10, 20, new Deleted()),
                read));
        assertThat(ConsistencyChecker.check(clean).violations()).isEmpty();

        History shrunk = Shrinker.shrink(clean, new Violation("read-after-delete", "s2", "p1", read));

        assertThat(shrunk).isEqualTo(clean);
    }

    @Test
    void shrink_WhenTheHistoryIsClean_ReturnsItUnchanged() {
        Read read = new Read("s1", "p1", 30, 40, new Found(5));
        History clean = History.of(List.of(new Save("s1", "p1", 10, 20, new Applied(5)), read));
        assertThat(ConsistencyChecker.check(clean).violations()).isEmpty();

        History shrunk = Shrinker.shrink(clean, new Violation("author-read-your-writes", "s1", "p1", read));

        assertThat(shrunk).isEqualTo(clean);
    }

    private static List<Operation> fillerAround(Delete delete, Read read, Random random) {
        List<Operation> operations = new ArrayList<>();
        for (int index = 0; index < HISTORY_SIZE - 2; index++) {
            String session = "s" + (1 + random.nextInt(SESSIONS));
            String key = "p" + random.nextInt(FILLER_KEYS);
            long invokedAt = 2L * index;
            operations.add(random.nextBoolean()
                    ? new Save(session, key, invokedAt, invokedAt + 1, new Applied(1))
                    : new Read(session, key, invokedAt, invokedAt + 1, new Found(1)));
        }
        operations.add(random.nextInt(operations.size() + 1), delete);
        int deleteAt = operations.indexOf(delete);
        operations.add(deleteAt + 1 + random.nextInt(operations.size() - deleteAt), read);
        return operations;
    }
}
