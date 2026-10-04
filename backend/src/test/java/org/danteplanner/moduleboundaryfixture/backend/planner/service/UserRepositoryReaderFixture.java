package org.danteplanner.moduleboundaryfixture.backend.planner.service;

import org.danteplanner.backend.user.repository.UserRepository;

public class UserRepositoryReaderFixture {

    private final UserRepository userRepository;

    public UserRepositoryReaderFixture(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserRepository userRepository() {
        return userRepository;
    }
}
