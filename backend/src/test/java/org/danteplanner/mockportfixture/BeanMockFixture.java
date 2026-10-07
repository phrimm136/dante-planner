package org.danteplanner.mockportfixture;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BeanMockFixture {

    @Mock
    private FixtureService service;

    @Spy
    private FixtureSeam seam;

    @Mock
    private FixtureValue value;

    public BeanMockFixture(@Mock FixtureSeam constructorSeam) {
    }

    void exercise(@Mock FixtureService parameterService) {
    }
}
