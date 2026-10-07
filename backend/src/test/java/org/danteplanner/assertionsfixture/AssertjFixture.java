package org.danteplanner.assertionsfixture;

import static org.assertj.core.api.Assertions.assertThat;

public class AssertjFixture {

    static void check(int value) {
        assertThat(value).isEqualTo(1);
    }
}
