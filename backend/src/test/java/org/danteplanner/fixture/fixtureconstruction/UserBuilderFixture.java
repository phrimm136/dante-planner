package org.danteplanner.fixture.fixtureconstruction;

import org.danteplanner.backend.user.entity.User;

public class UserBuilderFixture {

    static User user() {
        return User.builder().build();
    }
}
