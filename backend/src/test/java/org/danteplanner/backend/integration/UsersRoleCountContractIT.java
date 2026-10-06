package org.danteplanner.backend.integration;

import org.springframework.beans.factory.annotation.Autowired;

import org.danteplanner.backend.support.UsersRoleCountContract;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

class UsersRoleCountContractIT extends ExclusiveDatabaseSupport implements UsersRoleCountContract {

    @Autowired
    private Users users;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Users users() {
        return users;
    }

    @Override
    public User persisted(User user) {
        return userRepository.insert(user);
    }
}
