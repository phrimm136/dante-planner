package org.danteplanner.fixture.testtransaction;

import org.junit.jupiter.api.Test;

public class InheritingFixture extends TransactionalSupportFixture {

    @Test
    void subject_WhenTheSuperclassIsTransactional_RunsInATestTransaction() {
    }
}
