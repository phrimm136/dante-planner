package org.danteplanner.testtransactionfixture;

import org.springframework.transaction.annotation.Transactional;

public class TransactionalBeanFixture {

    @Transactional
    public void seed() {
    }
}
