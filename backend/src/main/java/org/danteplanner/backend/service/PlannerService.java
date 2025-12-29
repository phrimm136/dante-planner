package org.danteplanner.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.PlannerConflictException;
import org.danteplanner.backend.exception.PlannerLimitExceededException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerService {

    private static final int MAX_PLANNERS_PER_USER = 100;
    private static final int MAX_PLANNER_SIZE_BYTES = 50 * 1024; // 50KB
    private static final int MAX_NOTE_SIZE_BYTES = 1024; // 1KB per note

    private final ObjectMapper objectMapper;
    private final PlannerRepository plannerRepository;
    private final UserRepository userRepository;
    private final PlannerSseService sseService;

    /**
     * Create a new planner for a user.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request (for SSE notification exclusion)
     * @param req      the create planner request
     * @return the created planner response
     * @throws PlannerLimitExceededException if user has reached max planners
     * @throws PlannerValidationException if content exceeds size limit
     */
    @Transactional
    public PlannerResponse createPlanner(Long userId, UUID deviceId, CreatePlannerRequest req) {
        // Check planner count limit
        long currentCount = plannerRepository.countByUserIdAndDeletedAtIsNull(userId);
        if (currentCount >= MAX_PLANNERS_PER_USER) {
            throw new PlannerLimitExceededException(currentCount, MAX_PLANNERS_PER_USER);
        }

        // Validate content size
        validateContentSize(req.getContent());

        // Get user reference
        User user = userRepository.getReferenceById(userId);

        // Build and save planner
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .category(req.getCategory())
                .title(req.getTitle() != null ? req.getTitle() : "Untitled")
                .status(req.getStatus() != null ? req.getStatus() : "draft")
                .content(req.getContent())
                .deviceId(deviceId != null ? deviceId.toString() : null)
                .savedAt(Instant.now())
                .build();

        Planner saved = plannerRepository.save(planner);
        log.info("Created planner {} for user {}", saved.getId(), userId);

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, saved.getId(), "created");

        return PlannerResponse.fromEntity(saved);
    }

    /**
     * Get all planners for a user with pagination.
     *
     * @param userId   the user ID
     * @param pageable pagination information
     * @return page of planner summaries
     */
    @Transactional(readOnly = true)
    public Page<PlannerSummaryResponse> getPlanners(Long userId, Pageable pageable) {
        return plannerRepository
                .findByUserIdAndDeletedAtIsNullOrderByLastModifiedAtDesc(userId, pageable)
                .map(PlannerSummaryResponse::fromEntity);
    }

    /**
     * Get a specific planner by ID.
     *
     * @param userId the user ID
     * @param id the planner ID
     * @return the planner response
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional(readOnly = true)
    public PlannerResponse getPlanner(Long userId, UUID id) {
        Planner planner = findPlannerOrThrow(userId, id);
        return PlannerResponse.fromEntity(planner);
    }

    /**
     * Update an existing planner.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request (for SSE notification exclusion)
     * @param id       the planner ID
     * @param req      the update request
     * @return the updated planner response
     * @throws PlannerNotFoundException if planner not found
     * @throws PlannerConflictException if sync version mismatch
     * @throws PlannerValidationException if content exceeds size limit
     */
    @Transactional
    public PlannerResponse updatePlanner(Long userId, UUID deviceId, UUID id, UpdatePlannerRequest req) {
        Planner planner = findPlannerOrThrow(userId, id);

        // Check optimistic locking
        if (!planner.getSyncVersion().equals(req.getSyncVersion())) {
            throw new PlannerConflictException(req.getSyncVersion(), planner.getSyncVersion());
        }

        // Update fields if provided
        if (req.getTitle() != null) {
            planner.setTitle(req.getTitle());
        }
        if (req.getStatus() != null) {
            planner.setStatus(req.getStatus());
        }
        if (req.getContent() != null) {
            validateContentSize(req.getContent());
            planner.setContent(req.getContent());
        }
        if (deviceId != null) {
            planner.setDeviceId(deviceId.toString());
        }

        // Increment sync version
        planner.setSyncVersion(planner.getSyncVersion() + 1);
        planner.setSavedAt(Instant.now());

        Planner saved = plannerRepository.save(planner);
        log.info("Updated planner {} for user {}, new syncVersion: {}", id, userId, saved.getSyncVersion());

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, id, "updated");

        return PlannerResponse.fromEntity(saved);
    }

    /**
     * Soft delete a planner.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request (for SSE notification exclusion)
     * @param id       the planner ID
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public void deletePlanner(Long userId, UUID deviceId, UUID id) {
        Planner planner = findPlannerOrThrow(userId, id);
        planner.softDelete();
        plannerRepository.save(planner);
        log.info("Soft deleted planner {} for user {}", id, userId);

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, id, "deleted");
    }

    /**
     * Import multiple planners for a user.
     *
     * @param userId the user ID
     * @param req the import request
     * @return the import result
     * @throws PlannerLimitExceededException if import would exceed user's limit
     */
    @Transactional
    public ImportPlannersResponse importPlanners(Long userId, ImportPlannersRequest req) {
        long currentCount = plannerRepository.countByUserIdAndDeletedAtIsNull(userId);
        int requestedCount = req.getPlanners().size();

        if (currentCount + requestedCount > MAX_PLANNERS_PER_USER) {
            throw new PlannerLimitExceededException(currentCount, MAX_PLANNERS_PER_USER);
        }

        User user = userRepository.getReferenceById(userId);
        List<PlannerSummaryResponse> importedPlanners = new ArrayList<>();

        for (CreatePlannerRequest plannerReq : req.getPlanners()) {
            validateContentSize(plannerReq.getContent());

            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .category(plannerReq.getCategory())
                    .title(plannerReq.getTitle() != null ? plannerReq.getTitle() : "Untitled")
                    .status(plannerReq.getStatus() != null ? plannerReq.getStatus() : "draft")
                    .content(plannerReq.getContent())
                    .savedAt(Instant.now())
                    .build();

            Planner saved = plannerRepository.save(planner);
            importedPlanners.add(PlannerSummaryResponse.fromEntity(saved));
        }

        log.info("Imported {} planners for user {}", importedPlanners.size(), userId);

        return ImportPlannersResponse.builder()
                .imported(importedPlanners.size())
                .total(requestedCount)
                .planners(importedPlanners)
                .build();
    }

    /**
     * Find a planner by user ID and planner ID, or throw exception.
     */
    private Planner findPlannerOrThrow(Long userId, UUID id) {
        return plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    /**
     * Validate that content doesn't exceed size limit and individual notes are within bounds.
     */
    private void validateContentSize(String content) {
        if (content != null) {
            int size = content.getBytes(StandardCharsets.UTF_8).length;
            if (size > MAX_PLANNER_SIZE_BYTES) {
                throw new PlannerValidationException(
                        "CONTENT_TOO_LARGE",
                        "Content size (" + size + " bytes) exceeds maximum allowed (" + MAX_PLANNER_SIZE_BYTES + " bytes)");
            }
            // Also validate individual note sizes
            validateNoteSize(content);
        }
    }

    /**
     * Validate that individual notes don't exceed size limit (1KB each).
     * Notes are stored in sectionNotes field as Record<string, SerializableNoteContent>
     * where SerializableNoteContent has a 'content' field with JSONContent.
     *
     * @param content the planner content JSON string
     * @throws PlannerValidationException if any note exceeds 1KB
     */
    private void validateNoteSize(String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(content);
            JsonNode sectionNotesNode = rootNode.get("sectionNotes");

            if (sectionNotesNode == null || !sectionNotesNode.isObject()) {
                return;
            }

            for (Map.Entry<String, JsonNode> entry : sectionNotesNode.properties()) {
                String sectionKey = entry.getKey();
                JsonNode noteNode = entry.getValue();

                // Serialize the note to get its actual byte size
                String noteJson = objectMapper.writeValueAsString(noteNode);
                int noteSize = noteJson.getBytes(StandardCharsets.UTF_8).length;

                if (noteSize > MAX_NOTE_SIZE_BYTES) {
                    throw new PlannerValidationException(
                            "NOTE_TOO_LARGE",
                            "Note in section '" + sectionKey + "' (" + noteSize + " bytes) exceeds maximum allowed (" + MAX_NOTE_SIZE_BYTES + " bytes)");
                }
            }
        } catch (JsonProcessingException e) {
            // If we can't parse the JSON, let the existing validation handle it
            log.warn("Failed to parse content JSON for note validation: {}", e.getMessage());
        }
    }
}
