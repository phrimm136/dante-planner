package org.danteplanner.freshnessfixture;

import org.springframework.transaction.annotation.Transactional;

public class UndeclaredReadFixture {

    @Transactional(readOnly = true)
    public String undeclared() {
        return "reached";
    }
}
