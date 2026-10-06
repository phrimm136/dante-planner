package org.danteplanner.backend.integration;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Tag;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.api.Notifications;
import org.danteplanner.backend.support.NotificationsContract;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class NotificationDispatchContractIT extends SharedMySqlContainerSupport implements NotificationsContract {

    @Autowired
    private Notifications notifications;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public Notifications notifications() {
        return notifications;
    }

    @Override
    public User persisted(User user) {
        return userRepository.insert(user);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
