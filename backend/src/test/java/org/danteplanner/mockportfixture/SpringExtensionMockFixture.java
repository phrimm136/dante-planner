package org.danteplanner.mockportfixture;

import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(SpringExtension.class)
@ExtendWith(MockitoExtension.class)
public class SpringExtensionMockFixture {

    @Mock
    private FixtureRepository repository;
}
