package org.danteplanner.assertionsfixture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HamcrestAssertFixture {

    static void check(int value) {
        assertThat(value, is(1));
    }
}
