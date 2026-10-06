package org.danteplanner.testtransactionfixture;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

public class JakartaTransactionalFixture {

    @Test
    @Transactional
    void subject_WhenTheMethodIsJakartaTransactional_RunsInATestTransaction() {
    }
}
