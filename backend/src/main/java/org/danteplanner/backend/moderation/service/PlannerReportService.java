package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;

/**
 * Service for managing planner reports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerReportService {

    private final PlannerReportRepository reportRepository;
    private final PlannerAccessGuard accessGuard;
    private final ReportUniquenessValidator reportUniquenessValidator;

    @Transactional
    public PlannerReport createReport(Long userId, UUID plannerId) {
        accessGuard.checkNotBanned(userId);

        accessGuard.checkPublished(plannerId);

        reportUniquenessValidator.requireFirstPlannerReport(
                reportRepository.existsByUserIdAndPlannerId(userId, plannerId), plannerId, userId);

        PlannerReport report = new PlannerReport(userId, plannerId);
        PlannerReport saved = reportRepository.insert(report);
        log.info("User {} reported planner {}", userId, plannerId);
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean hasReported(Long userId, UUID plannerId) {
        return reportRepository.existsByUserIdAndPlannerId(userId, plannerId);
    }
}
