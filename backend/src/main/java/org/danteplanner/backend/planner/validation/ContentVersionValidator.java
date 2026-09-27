package org.danteplanner.backend.planner.validation;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ContentVersionValidator {

    private static final String INVALID_CONTENT_VERSION = "INVALID_CONTENT_VERSION";
    private static final String CONTENT_VERSION_REQUIRED = "CONTENT_VERSION_REQUIRED";

    private record VersionRule(Function<PlannerVersions, List<Integer>> forCreate, String displayName) {}

    private final GameDataRegistry gameDataRegistry;
    private final Map<PlannerType, VersionRule> rules;

    public ContentVersionValidator(GameDataRegistry gameDataRegistry) {
        this.gameDataRegistry = gameDataRegistry;

        Map<PlannerType, VersionRule> byType = new EnumMap<>(PlannerType.class);
        byType.put(PlannerType.MIRROR_DUNGEON,
                new VersionRule(versions -> List.of(versions.mdCurrentVersion()), "Mirror Dungeon"));
        byType.put(PlannerType.REFRACTED_RAILWAY,
                new VersionRule(PlannerVersions::rrAvailableVersions, "Refracted Railway"));
        this.rules = Map.copyOf(byType);

        requireEveryTypeCovered();
    }

    private void requireEveryTypeCovered() {
        List<PlannerType> uncovered = Arrays.stream(PlannerType.values())
                .filter(type -> !rules.containsKey(type))
                .toList();
        if (!uncovered.isEmpty()) {
            throw new IllegalStateException("No content version rule for planner type(s): " + uncovered);
        }
    }

    public void validateVersionForCreate(PlannerType plannerType, Integer contentVersion) {
        if (contentVersion == null) {
            log.warn("Validation failed: content version is null");
            throw new PlannerValidationException(CONTENT_VERSION_REQUIRED, "Content version is required");
        }

        VersionRule rule = rules.get(plannerType);
        List<Integer> accepted = rule.forCreate().apply(gameDataRegistry.plannerVersions());
        if (!accepted.contains(contentVersion)) {
            log.warn("Validation failed: {} create version {} not in {}",
                    rule.displayName(), contentVersion, accepted);
            throw new PlannerValidationException(INVALID_CONTENT_VERSION,
                    "Invalid content version for " + rule.displayName());
        }
    }
}
