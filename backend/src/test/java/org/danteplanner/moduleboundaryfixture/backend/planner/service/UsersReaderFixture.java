package org.danteplanner.moduleboundaryfixture.backend.planner.service;

import org.danteplanner.backend.user.api.Users;

public class UsersReaderFixture {

    private final Users users;

    public UsersReaderFixture(Users users) {
        this.users = users;
    }

    public Users users() {
        return users;
    }
}
