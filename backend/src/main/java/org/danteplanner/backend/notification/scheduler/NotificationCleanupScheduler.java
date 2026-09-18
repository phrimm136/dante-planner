package org.danteplanner.backend.notification.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.danteplanner.backend.notification.service.NotificationRetentionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code @SchedulerLock} over the shared auth Redis lock store ensures the job fires once across
 * the fleet, not once per pod.
 */
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationRetentionService retentionService;

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "cleanupOldNotifications", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void cleanupOldNotifications() {
        retentionService.purgeExpired();
    }
}
