package org.danteplanner.mockportfixture;

import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface FixtureRepository extends Repository<Object, Long> {
}
