package org.danteplanner.assertionsfixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JunitAssertionsFixture {

    static void check(int value) {
        assertEquals(1, value);
    }
}
