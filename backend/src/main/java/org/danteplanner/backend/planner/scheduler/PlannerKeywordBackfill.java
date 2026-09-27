package org.danteplanner.backend.planner.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.danteplanner.backend.planner.service.PlannerKeywordBackfillService;
import org.danteplanner.backend.planner.service.PlannerKeywordBackfillService.BackfillPage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlannerKeywordBackfill {

    private static final int PAGE_SIZE = 500;

    private final PlannerKeywordBackfillService backfillService;

    @Scheduled(initialDelayString = "${planner.keyword-backfill.initial-delay-ms:60000}")
    @SchedulerLock(name = "backfillPlannerKeywords", lockAtMostFor = "PT30M")
    public Integer backfill() {
        int corrected = 0;
        UUID after = null;
        BackfillPage page;
        do {
            page = backfillService.backfillPage(after, PAGE_SIZE);
            corrected += page.corrected();
            after = page.lastPlannerId();
        } while (page.scanned() == PAGE_SIZE);
        log.info("Planner keyword backfill corrected {} planner(s)", corrected);
        return corrected;
    }
}
