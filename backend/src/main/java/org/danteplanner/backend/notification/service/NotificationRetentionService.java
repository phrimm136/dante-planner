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

    private static final int READ_RETENTION_DAYS = 365;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional
    public void purgeExpired() {
        Instant cutoff = clock.instant().minus(READ_RETENTION_DAYS, ChronoUnit.DAYS);

        int deleted = notificationRepository.deleteReadBefore(cutoff);

        log.info("Notification cleanup complete: {} deleted", deleted);
    }
}
