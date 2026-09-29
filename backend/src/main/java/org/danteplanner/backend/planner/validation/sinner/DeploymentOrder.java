package org.danteplanner.backend.planner.validation.sinner;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.FieldResult;
import org.danteplanner.backend.planner.validation.JsonArrays;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class DeploymentOrder {

    static final int FIRST_SINNER_INDEX = 0;
    static final int LAST_SINNER_INDEX = 11;

    private static final ContentPath DEPLOYMENT_ORDER = ContentPath.of("deploymentOrder");

    private DeploymentOrder() {
    }

    public static List<Violation> check(JsonNode deploymentOrder) {
        FieldResult<List<Optional<Integer>>> positions = JsonArrays.ints(deploymentOrder, DEPLOYMENT_ORDER);
        List<Optional<Integer>> sinnerIndices = positions.value();
        return Stream.concat(
                        positions.violations().stream(),
                        IntStream.range(0, sinnerIndices.size())
                                .boxed()
                                .flatMap(position -> sinnerIndices.get(position)
                                        .filter(sinnerIndex -> !inRange(sinnerIndex))
                                        .map(sinnerIndex -> SinnerViolations.deploymentOutOfRange(
                                                DEPLOYMENT_ORDER.index(position), sinnerIndex))
                                        .stream()))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static boolean inRange(int sinnerIndex) {
        return sinnerIndex >= FIRST_SINNER_INDEX && sinnerIndex <= LAST_SINNER_INDEX;
    }
}
