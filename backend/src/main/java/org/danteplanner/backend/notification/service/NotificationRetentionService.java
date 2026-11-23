package org.danteplanner.backend.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * How long a notification is kept once it has stopped being useful to its recipient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRetentionService {

    private static final int SOFT_DELETE_AFTER_DAYS = 90;
    private static final int HARD_DELETE_AFTER_DAYS = 365;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional
    public void purgeExpired() {
        Instant now = clock.instant();
        Instant softDeleteCutoff = now.minus(SOFT_DELETE_AFTER_DAYS, ChronoUnit.DAYS);
        Instant hardDeleteCutoff = now.minus(HARD_DELETE_AFTER_DAYS, ChronoUnit.DAYS);

        int softDeleted = notificationRepository.softDeleteOldReadNotifications(softDeleteCutoff, now);
        int hardDeleted = notificationRepository.hardDeleteOldNotifications(hardDeleteCutoff);

        log.info("Notification cleanup complete: {} soft-deleted, {} hard-deleted", softDeleted, hardDeleted);
    }
}
