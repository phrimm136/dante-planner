package org.danteplanner.backend.repository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.integration.ExclusiveDatabaseSupport;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.notification.service.NotificationRetentionService;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRetentionIT extends ExclusiveDatabaseSupport {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long recipientId;

    @BeforeEach
    void setUp() {
        recipientId = TestDataFactory.createTestUser(userRepository, "retention@example.com").getId();
    }

    @Test
    void purgeExpired_WhenRowsWereRead_DeletesOnlyThoseReadOver365DaysAgo() {
        Long expired = notification(daysAgo(366));
        Long justExpired = notification(daysAgo(365).minus(1, ChronoUnit.MICROS));
        Long atTheCutoff = notification(daysAgo(365));
        Long kept = notification(daysAgo(364));
        Long unread = notification(null);

        NotificationRetentionService service =
                new NotificationRetentionService(notificationRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        transactionTemplate.executeWithoutResult(status -> service.purgeExpired());

        assertThat(notificationRepository.existsById(expired)).isFalse();
        assertThat(notificationRepository.existsById(justExpired)).isFalse();
        assertThat(notificationRepository.existsById(atTheCutoff)).isTrue();
        assertThat(notificationRepository.existsById(kept)).isTrue();
        assertThat(notificationRepository.existsById(unread)).isTrue();
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(days, ChronoUnit.DAYS);
    }

    private Long notification(@Nullable Instant readAt) {
        Long id = notificationRepository.save(new Notification(
                recipientId, "retention-" + UUID.randomUUID(), NotificationType.COMMENT_RECEIVED)).getId();
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE notifications SET created_at = :createdAt, read_at = :readAt WHERE id = :id")
                .setParameter("createdAt", daysAgo(400))
                .setParameter("readAt", readAt)
                .setParameter("id", id)
                .executeUpdate());
        return id;
    }
}
