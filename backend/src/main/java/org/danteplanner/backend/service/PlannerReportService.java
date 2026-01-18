package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.entity.PlannerReport;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.ReportAlreadyExistsException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.PlannerReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing planner reports.
 * Reports are immutable - create-only, no updates or deletes.
 */
@Service
@Slf4j
public class PlannerReportService {

    private final PlannerReportRepository reportRepository;
    private final PlannerRepository plannerRepository;

    public PlannerReportService(
            PlannerReportRepository reportRepository,
            PlannerRepository plannerRepository) {
        this.reportRepository = reportRepository;
        this.plannerRepository = plannerRepository;
    }

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
        // Verify planner exists and is published
        if (plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId).isEmpty()) {
            throw new PlannerNotFoundException(plannerId);
        }

        // Check if already reported (one report per user per planner)
        if (reportRepository.existsByUserIdAndPlannerId(userId, plannerId)) {
            throw new ReportAlreadyExistsException(plannerId, userId);
        }

        PlannerReport report = new PlannerReport(userId, plannerId);
        PlannerReport saved = reportRepository.save(report);
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
