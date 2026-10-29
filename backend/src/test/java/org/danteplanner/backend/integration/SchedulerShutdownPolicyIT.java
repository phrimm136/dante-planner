package org.danteplanner.backend.integration;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.config.ViewFlushSchedulerConfig;
import org.danteplanner.backend.shared.config.ReadSourcesConfig;
import org.danteplanner.backend.shared.freshness.MarkerStore;
import org.danteplanner.backend.shared.sse.SseHeartbeatWorkerConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class SchedulerShutdownPolicyIT extends SharedRoutingSupport {

    @Autowired
    private Map<String, ThreadPoolTaskScheduler> schedulers;

    @Test
    void schedulers_WhenReadFromTheRunningContext_DropDelayedTasksOnShutdown() {
        Map<String, Boolean> runsDelayedTasksAfterShutdown = new TreeMap<>();
        schedulers.forEach((name, scheduler) -> runsDelayedTasksAfterShutdown.put(name,
                scheduler.getScheduledThreadPoolExecutor().getExecuteExistingDelayedTasksAfterShutdownPolicy()));

        assertThat(runsDelayedTasksAfterShutdown)
                .containsKeys("taskScheduler", ViewFlushSchedulerConfig.VIEW_FLUSH_SCHEDULER,
                        ReadSourcesConfig.PROBE_SCHEDULER, MarkerStore.PROBE_SCHEDULER,
                        SseHeartbeatWorkerConfig.SSE_HEARTBEAT_WORKER)
                .allSatisfy((name, runsAfterShutdown) -> assertThat(runsAfterShutdown).as(name).isFalse());
    }
}
