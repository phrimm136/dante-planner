package org.danteplanner.displaynamefixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class UnnamedFixture {

    @Test
    void subject_WhenNamedOnce_CarriesOnlyTheMethodName() {
    }

    @Nested
    public class Group {

        @Test
        void subject_WhenNested_CarriesOnlyTheMethodName() {
        }
    }
}
