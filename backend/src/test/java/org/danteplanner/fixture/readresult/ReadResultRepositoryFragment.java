package org.danteplanner.fixture.readresult;

import java.util.Optional;

public interface ReadResultRepositoryFragment {

    Optional<ReadResultRepository.Row> findOrCreate(String name);
}
