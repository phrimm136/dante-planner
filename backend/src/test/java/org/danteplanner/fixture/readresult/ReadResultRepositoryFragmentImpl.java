package org.danteplanner.fixture.readresult;

import java.util.Optional;

public class ReadResultRepositoryFragmentImpl implements ReadResultRepositoryFragment {

    private final ReadResultRepository repository;

    public ReadResultRepositoryFragmentImpl(ReadResultRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ReadResultRepository.Row> findOrCreate(String name) {
        return Optional.of(repository.save(new ReadResultRepository.Row(name)));
    }
}
