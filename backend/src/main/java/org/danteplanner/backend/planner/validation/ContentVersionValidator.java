package org.danteplanner.backend.planner.validation;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates the content version a new planner declares against what its type accepts.
 */
@Component
@Slf4j
public class ContentVersionValidator {

    private static final String INVALID_CONTENT_VERSION = "INVALID_CONTENT_VERSION";
    private static final String CONTENT_VERSION_REQUIRED = "CONTENT_VERSION_REQUIRED";

    /**
     * The versions one planner type accepts, and the name its rejection message carries.
     *
     * @param forCreate   versions a new planner may declare
     * @param displayName the type's name as the client sees it
     */
    private record VersionRule(List<Integer> forCreate, String displayName) {}

    private final Map<PlannerType, VersionRule> rules;

    public ContentVersionValidator(
            @Value("${planner.md.current-version}") int mdCurrentVersion,
            @Value("${planner.rr.available-versions}") String rrAvailableVersionsRaw) {
        List<Integer> rrAvailableVersions = parseVersionList(rrAvailableVersionsRaw);

        Map<PlannerType, VersionRule> byType = new EnumMap<>(PlannerType.class);
        byType.put(PlannerType.MIRROR_DUNGEON,
                new VersionRule(List.of(mdCurrentVersion), "Mirror Dungeon"));
        byType.put(PlannerType.REFRACTED_RAILWAY,
                new VersionRule(rrAvailableVersions, "Refracted Railway"));
        this.rules = Map.copyOf(byType);

        requireEveryTypeCovered();
        log.info("ContentVersionValidator initialized: MD current={}, RR available={}",
                mdCurrentVersion, rrAvailableVersions);
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
                    .toList();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid version list format: '%s'. Must be comma-separated integers.", raw), e);
        }
    }

    /**
     * Validates the content version a new planner declares.
     *
     * @param plannerType    the planner type
     * @param contentVersion the content version to validate
     * @throws PlannerValidationException if the version is absent or the type does not accept it
     */
    public void validateVersionForCreate(PlannerType plannerType, Integer contentVersion) {
        if (contentVersion == null) {
            log.warn("Validation failed: content version is null");
            throw new PlannerValidationException(CONTENT_VERSION_REQUIRED, "Content version is required");
        }

        VersionRule rule = rules.get(plannerType);
        if (!rule.forCreate().contains(contentVersion)) {
            log.warn("Validation failed: {} create version {} not in {}",
                    rule.displayName(), contentVersion, rule.forCreate());
            throw new PlannerValidationException(INVALID_CONTENT_VERSION,
                    "Invalid content version for " + rule.displayName());
        }
    }
}
