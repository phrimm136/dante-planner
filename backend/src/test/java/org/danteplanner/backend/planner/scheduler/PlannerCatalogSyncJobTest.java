package org.danteplanner.backend.planner.scheduler;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.danteplanner.backend.planner.service.PlannerCatalogSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerCatalogSyncJobTest {

    @Mock
    private PlannerCatalogSyncService syncService;

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    @Test
    void backlogGauge_WhenScraped_ExportsTheAlertedNameWithTheOldestPendingAge() {
        when(syncService.backlogAgeSeconds()).thenReturn(75.0);
        new PlannerCatalogSyncJob(syncService, registry);

        assertThat(registry.scrape()).contains("planner_catalog_sync_backlog_age_seconds 75.0");
    }

    @Test
    void sync_WhenRun_DrainsThePendingEvents() {
        new PlannerCatalogSyncJob(syncService, registry).sync();

        verify(syncService).drainPending();
    }

    @Test
    void sync_WhenThePendingEventsAreUnreadable_SkipsThePass() {
        when(syncService.drainPending()).thenThrow(new QueryTimeoutException("down"));

        assertThatCode(() -> new PlannerCatalogSyncJob(syncService, registry).sync()).doesNotThrowAnyException();
    }
}
