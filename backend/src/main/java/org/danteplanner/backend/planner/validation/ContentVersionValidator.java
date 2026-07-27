package org.danteplanner.backend.planner.validation;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
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

    /**
     * The versions one planner type accepts, per operation, and the name its rejection message
     * carries.
     *
     * @param forCreate   versions a new planner may declare
     * @param forUpdate   versions an existing planner may keep
     * @param displayName the type's name as the client sees it
     */
    private record VersionRule(List<Integer> forCreate, List<Integer> forUpdate, String displayName) {}

    private final Map<PlannerType, VersionRule> rules;

    public ContentVersionValidator(
            @Value("${planner.md.current-version}") int mdCurrentVersion,
            @Value("${planner.md.available-versions}") String mdAvailableVersionsRaw,
            @Value("${planner.rr.available-versions}") String rrAvailableVersionsRaw) {
        List<Integer> mdAvailableVersions = parseVersionList(mdAvailableVersionsRaw);
        List<Integer> rrAvailableVersions = parseVersionList(rrAvailableVersionsRaw);

        Map<PlannerType, VersionRule> byType = new EnumMap<>(PlannerType.class);
        byType.put(PlannerType.MIRROR_DUNGEON,
                new VersionRule(List.of(mdCurrentVersion), mdAvailableVersions, "Mirror Dungeon"));
        byType.put(PlannerType.REFRACTED_RAILWAY,
                new VersionRule(rrAvailableVersions, rrAvailableVersions, "Refracted Railway"));
        this.rules = Map.copyOf(byType);

        requireEveryTypeCovered();
        log.info("ContentVersionValidator initialized: MD current={}, MD available={}, RR available={}",
                mdCurrentVersion, mdAvailableVersions, rrAvailableVersions);
    }

    /**
     * A planner type with no rule would pass every version unchecked, so its absence has to stop
     * the context from starting rather than surface as accepted bad content later.
     */
    private void requireEveryTypeCovered() {
        List<PlannerType> uncovered = Arrays.stream(PlannerType.values())
                .filter(type -> !rules.containsKey(type))
                .toList();
        if (!uncovered.isEmpty()) {
            throw new IllegalStateException("No content version rule for planner type(s): " + uncovered);
        }
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
        VersionRule rule = rules.get(plannerType);
        requireAccepted(rule, rule.forCreate(), contentVersion, "create");
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
        VersionRule rule = rules.get(plannerType);
        requireAccepted(rule, rule.forUpdate(), contentVersion, "update");
    }

    private void requireNonNull(Integer contentVersion) {
        if (contentVersion == null) {
            log.warn("Validation failed: content version is null");
            throw new PlannerValidationException(CONTENT_VERSION_REQUIRED, "Content version is required");
        }
    }

    private void requireAccepted(VersionRule rule, List<Integer> accepted, Integer contentVersion, String operation) {
        if (!accepted.contains(contentVersion)) {
            log.warn("Validation failed: {} {} version {} not in {}",
                    rule.displayName(), operation, contentVersion, accepted);
            throw new PlannerValidationException(INVALID_CONTENT_VERSION,
                    "Invalid content version for " + rule.displayName());
        }
    }
}
