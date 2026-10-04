package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;

import org.danteplanner.backend.moderation.api.PlannerReportPurge;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.moderation.repository.PlannerCommentReportRepository;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.user.api.AccountLifecycleParticipant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * What a permanent account deletion has to do to report rows.
 */
@Service
@RequiredArgsConstructor
public class ModerationAccountPurgeService implements AccountLifecycleParticipant, PlannerReportPurge {

    private final PlannerReportRepository plannerReportRepository;
    private final PlannerCommentReportRepository plannerCommentReportRepository;
    private final ModerationActionRepository moderationActionRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reassignToSentinel(Long userId, Long sentinelId) {
        moderationActionRepository.reassignActorToSentinel(userId, sentinelId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteReportsFor(List<UUID> plannerIds) {
        plannerCommentReportRepository.deleteAllByPlannerIds(plannerIds);
        plannerReportRepository.deleteAllByPlannerIds(plannerIds);
    }
}
