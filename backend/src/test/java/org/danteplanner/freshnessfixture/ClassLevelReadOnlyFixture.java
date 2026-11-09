package org.danteplanner.freshnessfixture;

import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class ClassLevelReadOnlyFixture {

    public String inherited() {
        return "reached";
    }
}
