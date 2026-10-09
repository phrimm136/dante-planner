package org.danteplanner.backend.architecture.fixture.consistency;

import org.springframework.util.StringUtils;

public final class SpringImportingChecker {

    public boolean judge(String history) {
        return StringUtils.hasText(history);
    }
}
