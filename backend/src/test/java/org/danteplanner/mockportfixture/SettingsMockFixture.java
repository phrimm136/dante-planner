package org.danteplanner.mockportfixture;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
public class SettingsMockFixture {

    @Mock
    private FixtureRepository repository;
}
