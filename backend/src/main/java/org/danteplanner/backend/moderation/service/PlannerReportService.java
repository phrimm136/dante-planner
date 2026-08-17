package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.moderation.exception.ReportAlreadyExistsException;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;

/**
 * Service for managing planner reports.
 * Reports are immutable - create-only, no updates or deletes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerReportService {

    private final PlannerReportRepository reportRepository;
    private final PlannerAccessGuard accessGuard;
    private final ReportUniquenessValidator reportUniquenessValidator;

    /**
     * Create a report for a planner.
     * One-time action - throws exception if already reported.
     *
     * @param userId    the user ID submitting the report
     * @param plannerId the planner ID being reported
     * @return the created report
     * @throws PlannerNotFoundException    if planner not found or not published
     * @throws ReportAlreadyExistsException if user has already reported this planner
     */
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

    /**
     * Check if a user has already reported a planner.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return true if already reported, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasReported(Long userId, UUID plannerId) {
        return reportRepository.existsByUserIdAndPlannerId(userId, plannerId);
    }
}
