package org.danteplanner.backend.planner.scheduler;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.danteplanner.backend.planner.service.PlannerCatalogSyncService;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerCatalogSyncJobTest {

    @Mock
    private DomainEventRepository events;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    private PlannerCatalogSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new PlannerCatalogSyncService(events, null, null, null, null, null, null, null,
                transactionManager);
    }

    @Test
    void backlogGauge_WhenScraped_ExportsTheAlertedNameWithTheOldestPendingAge() {
        when(events.oldestPendingAgeSecondsOf(eq(DomainEventType.PLANNER_CATALOG_CHANGED.name()), anyInt()))
                .thenReturn(75L);
        new PlannerCatalogSyncJob(syncService, registry);

        assertThat(registry.scrape()).contains("planner_catalog_sync_backlog_age_seconds 75.0");
    }

    @Test
    void sync_WhenRun_DrainsThePendingEvents() {
        new PlannerCatalogSyncJob(syncService, registry).sync();

        verify(events).pendingEventsOfType(eq(DomainEventType.PLANNER_CATALOG_CHANGED), anyInt(), any(Pageable.class));
    }

    @Test
    void sync_WhenThePendingEventsAreUnreadable_SkipsThePass() {
        when(events.pendingEventsOfType(eq(DomainEventType.PLANNER_CATALOG_CHANGED), anyInt(), any(Pageable.class)))
                .thenThrow(new QueryTimeoutException("down"));

        assertThatCode(() -> new PlannerCatalogSyncJob(syncService, registry).sync()).doesNotThrowAnyException();
    }
}
