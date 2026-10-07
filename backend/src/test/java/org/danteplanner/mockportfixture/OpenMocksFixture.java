package org.danteplanner.mockportfixture;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class OpenMocksFixture {

    @Mock
    private FixtureRepository repository;

    void open() {
        MockitoAnnotations.openMocks(this);
    }
}
