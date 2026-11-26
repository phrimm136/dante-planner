package org.danteplanner.fixture.optionalsignature.scoped.repository;

import java.util.Optional;

public interface OptionalSignatureFixtureRepository {

    Optional<String> findByName(String name);

    default Optional<String> findFirst() {
        return findByName("");
    }
}
