package org.danteplanner.backend.planner.precondition;

import java.util.List;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.shared.failure.Parsed;
import org.jspecify.annotations.Nullable;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.gamedata.PlannerVersions;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ContentVersionValidator {

    private static final String INVALID_CONTENT_VERSION = "INVALID_CONTENT_VERSION";
    private static final String CONTENT_VERSION_REQUIRED = "CONTENT_VERSION_REQUIRED";

    private record VersionRule(Function<PlannerVersions, List<Integer>> forCreate, String displayName) {}

    private static final VersionRule MIRROR_DUNGEON =
            new VersionRule(versions -> List.of(versions.mdCurrentVersion()), "Mirror Dungeon");
    private static final VersionRule REFRACTED_RAILWAY =
            new VersionRule(PlannerVersions::rrAvailableVersions, "Refracted Railway");

    private final GameDataRegistry gameDataRegistry;

    public ContentVersionValidator(GameDataRegistry gameDataRegistry) {
        this.gameDataRegistry = gameDataRegistry;
    }

    private static VersionRule ruleFor(PlannerType plannerType) {
        return switch (plannerType) {
            case MIRROR_DUNGEON -> MIRROR_DUNGEON;
            case REFRACTED_RAILWAY -> REFRACTED_RAILWAY;
        };
    }

    public CreatableVersion requireCreatableVersion(PlannerType plannerType, @Nullable Integer contentVersion) {
        int named = CreatableVersion.named(contentVersion);
        VersionRule rule = ruleFor(plannerType);
        return CreatableVersion.parse(plannerType, named, rule.displayName(),
                rule.forCreate().apply(gameDataRegistry.plannerVersions()));
    }

    @Parsed
    public record CreatableVersion(PlannerType plannerType, int value) {

        private static int named(@Nullable Integer contentVersion) {
            if (contentVersion == null) {
                log.warn("Validation failed: content version is null");
                throw new PlannerValidationException(CONTENT_VERSION_REQUIRED, "Content version is required");
            }
            return contentVersion;
        }

        private static CreatableVersion parse(PlannerType plannerType, int named, String displayName,
                List<Integer> accepted) {
            if (!accepted.contains(named)) {
                log.warn("Validation failed: {} create version {} not in {}", displayName, named, accepted);
                throw new PlannerValidationException(INVALID_CONTENT_VERSION,
                        "Invalid content version for " + displayName);
            }
            return new CreatableVersion(plannerType, named);
        }
    }
}
