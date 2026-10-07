package org.danteplanner.fixturefixture;

import org.danteplanner.backend.user.entity.User;

public class UserBuilderFixture {

    static User user() {
        return User.builder().build();
    }
}
