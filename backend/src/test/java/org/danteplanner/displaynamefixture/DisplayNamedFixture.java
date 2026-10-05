package org.danteplanner.displaynamefixture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a display-named fixture")
public class DisplayNamedFixture {

    @Test
    @DisplayName("a display-named test")
    void subject_WhenNamedTwice_CarriesADisplayName() {
    }
}
