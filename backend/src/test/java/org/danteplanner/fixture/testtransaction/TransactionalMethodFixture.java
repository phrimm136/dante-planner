package org.danteplanner.fixture.testtransaction;

import org.junit.jupiter.api.Test;

import org.springframework.transaction.annotation.Transactional;

public class TransactionalMethodFixture {

    @Test
    @Transactional
    void subject_WhenTheMethodIsTransactional_RunsInATestTransaction() {
    }
}
