package org.danteplanner.backend.planner.scheduler;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.danteplanner.backend.planner.service.PlannerCatalogSyncService;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlannerCatalogSyncJob {

    static final String BACKLOG_AGE_GAUGE = "planner.catalog.sync.backlog.age";

    private static final String SECONDS = "seconds";

    private final PlannerCatalogSyncService syncService;

    public PlannerCatalogSyncJob(PlannerCatalogSyncService syncService, MeterRegistry meterRegistry) {
        this.syncService = syncService;
        Gauge.builder(BACKLOG_AGE_GAUGE, syncService, PlannerCatalogSyncService::backlogAgeSeconds)
                .baseUnit(SECONDS)
                .register(meterRegistry);
    }

    @Scheduled(initialDelayString = "${planner.catalog-sync.fixed-delay-ms}",
            fixedDelayString = "${planner.catalog-sync.fixed-delay-ms}")
    @SchedulerLock(name = "syncPlannerCatalog", lockAtMostFor = "PT5M")
    public void sync() {
        try {
            syncService.drainPending();
        } catch (DataAccessException e) {
            log.warn("Catalog sync pass skipped: pending events unreadable", e);
        }
    }
}
