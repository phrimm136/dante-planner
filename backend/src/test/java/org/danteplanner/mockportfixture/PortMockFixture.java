package org.danteplanner.mockportfixture;

import java.time.Clock;
import java.util.function.Supplier;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PortMockFixture {

    @Mock
    private FixtureRepository repository;

    @Mock
    private Clock clock;

    void exercise(@Mock Clock parameterClock) {
    }

    @Nested
    class NestedPortMocks {

        @Mock
        private Supplier<String> supplier;
    }
}
