package org.danteplanner.fixture.roleclaim;

import org.springframework.security.core.Authentication;

public class AuthorityReaderFixture {

    public int authorityCount(Authentication authentication) {
        return authentication.getAuthorities().size();
    }
}
