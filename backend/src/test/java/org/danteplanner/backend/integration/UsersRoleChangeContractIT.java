package org.danteplanner.backend.integration;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import org.danteplanner.backend.support.UsersRoleChangeContract;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

class UsersRoleChangeContractIT extends ExclusiveDatabaseSupport implements UsersRoleChangeContract {

    @Autowired
    private Users users;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public Users users() {
        return users;
    }

    @Override
    public User persisted(User user) {
        return userRepository.insert(user);
    }

    @Override
    public void leaveNoAdministrator() {
        jdbcTemplate.update("UPDATE users SET role = 'NORMAL' WHERE role = 'ADMIN'");
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
