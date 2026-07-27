package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;

import org.danteplanner.backend.moderation.repository.PlannerCommentReportRepository;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * What a permanent account deletion has to do to report rows.
 *
 * <p>Reports hold no-action foreign keys to the planner core and to comments, so they block the
 * cascade the user delete relies on and have to go first.</p>
 *
 * <p>Demands the caller's transaction: dropping reports in a transaction of their own would
 * destroy the moderation record even if the deletion it was part of then failed.</p>
 */
@Service
@RequiredArgsConstructor
public class ModerationAccountPurgeService {

    private final PlannerReportRepository plannerReportRepository;
    private final PlannerCommentReportRepository plannerCommentReportRepository;

    /**
     * Drop every report filed against the given planners or against comments on them.
     *
     * @param plannerIds the planners being removed
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteReportsFor(List<UUID> plannerIds) {
        plannerCommentReportRepository.deleteAllByPlannerIds(plannerIds);
        plannerReportRepository.deleteAllByPlannerIds(plannerIds);
    }
}
