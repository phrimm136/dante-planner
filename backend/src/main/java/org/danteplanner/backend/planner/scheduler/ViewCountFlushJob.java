package org.danteplanner.backend.planner.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.danteplanner.backend.planner.service.RedisViewRecorder;
import org.danteplanner.backend.planner.service.ViewCountFlushService;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class ViewCountFlushJob {

    static final String BATCH_FAILED_COUNTER = "views.flush.batch.failed";

    private final RedisViewRecorder redisViewRecorder;
    private final ViewCountFlushService viewCountFlushService;
    private final Counter batchFailed;

    public ViewCountFlushJob(
            RedisViewRecorder redisViewRecorder,
            ViewCountFlushService viewCountFlushService,
            MeterRegistry meterRegistry) {
        this.redisViewRecorder = redisViewRecorder;
        this.viewCountFlushService = viewCountFlushService;
        this.batchFailed = Counter.builder(BATCH_FAILED_COUNTER).register(meterRegistry);
    }

    @Scheduled(initialDelayString = "${planner.view-flush.fixed-delay-ms}",
            fixedDelayString = "${planner.view-flush.fixed-delay-ms}")
    @SchedulerLock(name = "flushViewCounts", lockAtMostFor = "PT10M")
    public void flush() {
        Set<String> leftovers;
        try {
            leftovers = redisViewRecorder.leftoverBatchIds();
        } catch (DataAccessException e) {
            log.warn("View flush skipped: view store unavailable", e);
            return;
        }
        leftovers.forEach(this::replayLeftover);

        UUID batchId = UUID.randomUUID();
        if (redisViewRecorder.claimBuffer(batchId)) {
            applyAndDelete(batchId);
        }
    }

    private void replayLeftover(String batchId) {
        try {
            applyAndDelete(UUID.fromString(batchId));
        } catch (RuntimeException e) {
            batchFailed.increment();
            log.error("Leftover view batch {} not applied", batchId, e);
            Sentry.captureException(e);
        }
    }

    private void applyAndDelete(UUID batchId) {
        viewCountFlushService.applyBatch(batchId, redisViewRecorder.batchIncrements(batchId));
        redisViewRecorder.deleteBatch(batchId);
    }
}
