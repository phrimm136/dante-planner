package org.danteplanner.fixture.optionalsignature.scoped;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class OptionalSignatureFixture {

    private final Optional<String> held;

    public OptionalSignatureFixture(Optional<String> held) {
        this.held = held;
    }

    public Optional<String> returned() {
        return held;
    }

    public void taken(Optional<String> value) {
    }

    public List<Optional<String>> nested() {
        return List.of();
    }

    public String receivedLocally() {
        Optional<String> found = Optional.of("found");
        return found.orElse("");
    }

    public List<String> mappedInALambda() {
        return Stream.of(Optional.of("found")).map(value -> value.orElse("")).toList();
    }
}
