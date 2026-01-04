package org.danteplanner.backend.validation;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates content version against planner type requirements.
 *
 * <p>Two validation modes:
 * <ul>
 *   <li><b>Create</b>: Strict - must use current version only</li>
 *   <li><b>Update</b>: Lenient - accepts any available version (legacy support)</li>
 * </ul>
 *
 * <p>This design allows existing planners with older versions to remain editable
 * while requiring new planners to use the current version.
 */
@Component
@Slf4j
public class ContentVersionValidator {

    private static final String INVALID_CONTENT_VERSION = "INVALID_CONTENT_VERSION";
    private static final String CONTENT_VERSION_REQUIRED = "CONTENT_VERSION_REQUIRED";

    private final int mdCurrentVersion;
    private final List<Integer> mdAvailableVersions;
    private final List<Integer> rrAvailableVersions;

    public ContentVersionValidator(
            @Value("${planner.md.current-version}") int mdCurrentVersion,
            @Value("${planner.md.available-versions}") String mdAvailableVersionsRaw,
            @Value("${planner.rr.available-versions}") String rrAvailableVersionsRaw) {
        this.mdCurrentVersion = mdCurrentVersion;
        this.mdAvailableVersions = parseVersionList(mdAvailableVersionsRaw);
        this.rrAvailableVersions = parseVersionList(rrAvailableVersionsRaw);
        log.info("ContentVersionValidator initialized: MD current={}, MD available={}, RR available={}",
                mdCurrentVersion, mdAvailableVersions, rrAvailableVersions);
    }

    private List<Integer> parseVersionList(String raw) {
        try {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid version list format: '%s'. Must be comma-separated integers.", raw), e);
        }
    }

    /**
     * Validate content version for creating a new planner.
     * Strict validation: Must use current version.
     *
     * @param plannerType    the planner type
     * @param contentVersion the content version to validate
     * @throws PlannerValidationException if version is invalid
     */
    public void validateVersionForCreate(PlannerType plannerType, Integer contentVersion) {
        requireNonNull(contentVersion);

        if (plannerType == PlannerType.MIRROR_DUNGEON) {
            if (!contentVersion.equals(mdCurrentVersion)) {
                log.warn("Validation failed: MD create version {} != current {}", contentVersion, mdCurrentVersion);
                throw new PlannerValidationException(INVALID_CONTENT_VERSION,
                        "Invalid content version for Mirror Dungeon");
            }
        } else if (plannerType == PlannerType.REFRACTED_RAILWAY) {
            validateRrVersion(contentVersion, "create");
        }
    }

    /**
     * Validate content version for updating an existing planner.
     * Lenient validation: Accepts any available version (legacy support).
     *
     * @param plannerType    the planner type
     * @param contentVersion the content version to validate
     * @throws PlannerValidationException if version is invalid
     */
    public void validateVersionForUpdate(PlannerType plannerType, Integer contentVersion) {
        requireNonNull(contentVersion);

        if (plannerType == PlannerType.MIRROR_DUNGEON) {
            if (!mdAvailableVersions.contains(contentVersion)) {
                log.warn("Validation failed: MD update version {} not in {}", contentVersion, mdAvailableVersions);
                throw new PlannerValidationException(INVALID_CONTENT_VERSION,
                        "Invalid content version for Mirror Dungeon");
            }
        } else if (plannerType == PlannerType.REFRACTED_RAILWAY) {
            validateRrVersion(contentVersion, "update");
        }
    }

    private void requireNonNull(Integer contentVersion) {
        if (contentVersion == null) {
            log.warn("Validation failed: content version is null");
            throw new PlannerValidationException(CONTENT_VERSION_REQUIRED, "Content version is required");
        }
    }

    private void validateRrVersion(Integer contentVersion, String operation) {
        if (!rrAvailableVersions.contains(contentVersion)) {
            log.warn("Validation failed: RR {} version {} not in {}", operation, contentVersion, rrAvailableVersions);
            throw new PlannerValidationException(INVALID_CONTENT_VERSION,
                    "Invalid content version for Refracted Railway");
        }
    }
}
