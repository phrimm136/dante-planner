package org.danteplanner.fixture.consistencychecker;

import org.springframework.util.StringUtils;

public final class SpringImportingChecker {

    public boolean judge(String history) {
        return StringUtils.hasText(history);
    }
}
