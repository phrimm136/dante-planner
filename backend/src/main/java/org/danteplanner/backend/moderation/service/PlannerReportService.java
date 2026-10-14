package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.api.PlannerModerationTarget;
import org.danteplanner.backend.moderation.api.PlannerReports;
import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing planner reports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerReportService implements PlannerReports {

    private final PlannerReportRepository reportRepository;
    private final PlannerModerationTarget plannerTarget;
    private final ReportUniquenessValidator reportUniquenessValidator;

    @Override
    @Transactional
    public void createReport(Long userId, UUID plannerId) {
        plannerTarget.checkPublished(plannerId);

        reportUniquenessValidator.requireFirstPlannerReport(
                reportRepository.existsByUserIdAndPlannerId(userId, plannerId), plannerId, userId);

        PlannerReport report = new PlannerReport(userId, plannerId);
        reportRepository.insert(report);
        log.info("User {} reported planner {}", userId, plannerId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasReported(Long userId, UUID plannerId) {
        return reportRepository.existsByUserIdAndPlannerId(userId, plannerId);
    }
}
