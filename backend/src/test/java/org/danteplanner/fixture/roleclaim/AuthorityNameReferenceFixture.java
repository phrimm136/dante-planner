package org.danteplanner.fixture.roleclaim;

import java.util.function.Function;

import org.springframework.security.core.GrantedAuthority;

public class AuthorityNameReferenceFixture {

    public Function<GrantedAuthority, String> authorityName() {
        return GrantedAuthority::getAuthority;
    }
}
