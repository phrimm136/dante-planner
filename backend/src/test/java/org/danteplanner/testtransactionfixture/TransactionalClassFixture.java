package org.danteplanner.testtransactionfixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.transaction.annotation.Transactional;

@Transactional
public class TransactionalClassFixture {

    @Test
    void subject_WhenTheClassIsTransactional_RunsInATestTransaction() {
    }

    @Nested
    public class Group {

        @Test
        void subject_WhenTheEnclosingClassIsTransactional_RunsInATestTransaction() {
        }
    }
}
