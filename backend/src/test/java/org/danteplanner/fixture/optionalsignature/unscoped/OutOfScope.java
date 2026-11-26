package org.danteplanner.fixture.optionalsignature.unscoped;

import java.util.Optional;

public class OutOfScope {

    public Optional<String> returned() {
        return Optional.empty();
    }
}
