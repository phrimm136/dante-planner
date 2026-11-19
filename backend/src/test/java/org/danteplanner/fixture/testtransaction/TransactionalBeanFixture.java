package org.danteplanner.fixture.testtransaction;

import org.springframework.transaction.annotation.Transactional;

public class TransactionalBeanFixture {

    @Transactional
    public void seed() {
    }
}
