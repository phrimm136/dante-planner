package org.danteplanner.backend.planner.validation.sinner;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.FieldResult;
import org.danteplanner.backend.planner.validation.JsonArrays;
import org.danteplanner.backend.planner.validation.Positions;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class DeploymentOrder {

    static final int FIRST_SINNER_INDEX = 0;
    static final int LAST_SINNER_INDEX = 11;
    static final int MAX_DEPLOYED = LAST_SINNER_INDEX - FIRST_SINNER_INDEX + 1;

    private static final ContentPath DEPLOYMENT_ORDER = ContentPath.of("deploymentOrder");

    private DeploymentOrder() {
    }

    public static List<Violation> check(JsonNode deploymentOrder) {
        FieldResult<List<Optional<Integer>>> positions = JsonArrays.ints(deploymentOrder, DEPLOYMENT_ORDER);
        List<Optional<Integer>> sinnerIndices = positions.value();
        return Stream.of(
                        sinnerIndices.size() > MAX_DEPLOYED
                                ? Stream.of(SinnerViolations.tooManyDeployed(DEPLOYMENT_ORDER, sinnerIndices.size()))
                                : Stream.<Violation>empty(),
                        positions.violations().stream(),
                        outOfRange(sinnerIndices),
                        repeated(sinnerIndices))
                .flatMap(violations -> violations)
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> outOfRange(List<Optional<Integer>> sinnerIndices) {
        return IntStream.range(0, sinnerIndices.size())
                .boxed()
                .flatMap(position -> sinnerIndices.get(position)
                        .filter(sinnerIndex -> !inRange(sinnerIndex))
                        .map(sinnerIndex -> SinnerViolations.deploymentOutOfRange(
                                DEPLOYMENT_ORDER.index(position), sinnerIndex))
                        .stream());
    }

    private static Stream<Violation> repeated(List<Optional<Integer>> sinnerIndices) {
        List<Optional<Integer>> deployed = sinnerIndices.stream()
                .map(sinnerIndex -> sinnerIndex.filter(DeploymentOrder::inRange))
                .toList();
        Map<Optional<Integer>, Integer> firstPosition = Positions.firstIndexOf(deployed);
        return IntStream.range(0, deployed.size())
                .boxed()
                .flatMap(position -> deployed.get(position)
                        .filter(sinnerIndex -> firstPosition.get(deployed.get(position)) < position)
                        .map(sinnerIndex -> SinnerViolations.deployedTwice(DEPLOYMENT_ORDER.index(position),
                                DEPLOYMENT_ORDER, sinnerIndex))
                        .stream());
    }

    private static boolean inRange(int sinnerIndex) {
        return sinnerIndex >= FIRST_SINNER_INDEX && sinnerIndex <= LAST_SINNER_INDEX;
    }
}
