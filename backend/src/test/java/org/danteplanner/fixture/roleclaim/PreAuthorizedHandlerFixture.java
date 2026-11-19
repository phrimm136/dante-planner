package org.danteplanner.fixture.roleclaim;

import org.springframework.security.access.prepost.PreAuthorize;

public class PreAuthorizedHandlerFixture {

    @PreAuthorize("hasRole('MODERATOR')")
    public void hide() {
    }
}
