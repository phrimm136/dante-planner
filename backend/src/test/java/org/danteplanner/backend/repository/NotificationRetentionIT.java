package org.danteplanner.backend.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

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

    private static final Instant CUTOFF = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(30, ChronoUnit.DAYS);
    private static final Instant STAMP = CUTOFF.plus(1, ChronoUnit.DAYS);
    private static final Instant EARLIER_STAMP = CUTOFF.minus(10, ChronoUnit.DAYS);
    private static final Instant JUST_BEFORE_CUTOFF = CUTOFF.minus(1, ChronoUnit.MICROS);
    private static final Instant LONG_BEFORE_CUTOFF = CUTOFF.minus(30, ChronoUnit.DAYS);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRetentionService retentionService;

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
    void softDeleteOldReadNotifications_WhenReadAndCreatedBeforeTheCutoff_StampsTheGivenDeletedAt() {
        Long old = notification(true, JUST_BEFORE_CUTOFF, null);

        softDelete();

        assertThat(deletedAt(old)).contains(STAMP);
    }

    @Test
    void softDeleteOldReadNotifications_WhenUnread_KeepsTheRowLive() {
        Long unread = notification(false, LONG_BEFORE_CUTOFF, null);

        softDelete();

        assertThat(deletedAt(unread)).isEmpty();
    }

    @Test
    void softDeleteOldReadNotifications_WhenCreatedExactlyAtTheCutoff_KeepsTheRowLive() {
        Long atCutoff = notification(true, CUTOFF, null);

        softDelete();

        assertThat(deletedAt(atCutoff)).isEmpty();
    }

    @Test
    void softDeleteOldReadNotifications_WhenAlreadySoftDeleted_KeepsTheEarlierStamp() {
        Long alreadyDeleted = notification(true, LONG_BEFORE_CUTOFF, EARLIER_STAMP);

        softDelete();

        assertThat(deletedAt(alreadyDeleted)).contains(EARLIER_STAMP);
    }

    @Test
    void hardDeleteOldNotifications_WhenSoftDeletedBeforeTheCutoff_RemovesReadAndUnreadRows() {
        Long read = notification(true, LONG_BEFORE_CUTOFF, JUST_BEFORE_CUTOFF);
        Long unread = notification(false, LONG_BEFORE_CUTOFF, JUST_BEFORE_CUTOFF);

        hardDelete();

        assertThat(notificationRepository.existsById(read)).isFalse();
        assertThat(notificationRepository.existsById(unread)).isFalse();
    }

    @Test
    void hardDeleteOldNotifications_WhenSoftDeletedExactlyAtTheCutoff_KeepsTheRow() {
        Long atCutoff = notification(true, LONG_BEFORE_CUTOFF, CUTOFF);

        hardDelete();

        assertThat(notificationRepository.existsById(atCutoff)).isTrue();
    }

    @Test
    void hardDeleteOldNotifications_WhenLive_KeepsTheRow() {
        Long live = notification(true, LONG_BEFORE_CUTOFF, null);

        hardDelete();

        assertThat(notificationRepository.existsById(live)).isTrue();
    }

    @Test
    void purgeExpired_WhenRowsOutliveTheirRetention_SoftDeletesOldReadRowsAndRemovesLongDeletedOnes() {
        Instant now = Instant.now();
        Long oldRead = notification(true, now.minus(91, ChronoUnit.DAYS), null);
        Long longDeleted = notification(true, now.minus(400, ChronoUnit.DAYS), now.minus(366, ChronoUnit.DAYS));

        retentionService.purgeExpired();

        assertThat(deletedAt(oldRead)).isPresent();
        assertThat(notificationRepository.existsById(longDeleted)).isFalse();
    }

    private void softDelete() {
        transactionTemplate.executeWithoutResult(status ->
                notificationRepository.softDeleteOldReadNotifications(CUTOFF, STAMP));
    }

    private void hardDelete() {
        transactionTemplate.executeWithoutResult(status ->
                notificationRepository.hardDeleteOldNotifications(CUTOFF));
    }

    private Optional<Instant> deletedAt(Long id) {
        return Optional.ofNullable(notificationRepository.findById(id).orElseThrow().getDeletedAt());
    }

    private Long notification(boolean read, Instant createdAt, Instant deletedAt) {
        Notification notification = new Notification(
                recipientId, "retention-" + UUID.randomUUID(), NotificationType.COMMENT_RECEIVED);
        if (read) {
            notification.markAsRead();
        }
        Long id = notificationRepository.save(notification).getId();
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("UPDATE notifications SET created_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", id)
                    .executeUpdate();
            if (deletedAt != null) {
                entityManager.createNativeQuery("UPDATE notifications SET deleted_at = :deletedAt WHERE id = :id")
                        .setParameter("deletedAt", deletedAt)
                        .setParameter("id", id)
                        .executeUpdate();
            }
        });
        return id;
    }
}
