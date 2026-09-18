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

@Component
@Slf4j
public class ContentVersionValidator {

    private static final String INVALID_CONTENT_VERSION = "INVALID_CONTENT_VERSION";
    private static final String CONTENT_VERSION_REQUIRED = "CONTENT_VERSION_REQUIRED";

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
