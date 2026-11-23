package org.danteplanner.backend.planner.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.danteplanner.backend.planner.service.PlannerKeywordBackfillService.BackfillCursor;
import org.danteplanner.backend.planner.service.PlannerKeywordBackfillService;
import org.danteplanner.backend.planner.service.PlannerKeywordBackfillService.BackfillPage;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class PlannerKeywordBackfill {

    private static final int PAGE_SIZE = 500;

    private final PlannerKeywordBackfillService backfillService;

    private volatile boolean closing;

    @Scheduled(initialDelayString = "${planner.keyword-backfill.initial-delay-ms:60000}")
    @SchedulerLock(name = "backfillPlannerKeywords", lockAtMostFor = "PT30M")
    public Integer backfill() {
        int corrected = 0;
        BackfillCursor cursor = new BackfillCursor.Start();
        BackfillPage page;
        do {
            page = backfillService.backfillPage(cursor, PAGE_SIZE);
            corrected += page.corrected();
            cursor = page.next();
        } while (page.scanned() == PAGE_SIZE && !closing);
        log.info("Planner keyword backfill corrected {} planner(s)", corrected);
        return corrected;
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        closing = true;
    }
}
