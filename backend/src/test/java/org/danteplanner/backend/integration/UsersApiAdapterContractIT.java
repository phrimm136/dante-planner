package org.danteplanner.backend.integration;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Tag;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.support.UsersContract;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class UsersApiAdapterContractIT extends SharedMySqlContainerSupport implements UsersContract {

    @Autowired
    private Users users;

    @Autowired
    private UserRepository userRepository;

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
    public Long liveSentinelId() {
        return Users.SENTINEL_USER_ID;
    }

    @Override
    public Class<? extends RuntimeException> duplicateKeyFailure() {
        return DataIntegrityViolationException.class;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
