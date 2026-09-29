package org.danteplanner.roleclaimfixture;

import org.springframework.security.core.Authentication;

public class AuthorityReaderFixture {

    public int authorityCount(Authentication authentication) {
        return authentication.getAuthorities().size();
    }
}
