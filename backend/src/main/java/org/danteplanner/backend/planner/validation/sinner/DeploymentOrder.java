package org.danteplanner.backend.planner.validation.sinner;

import org.danteplanner.backend.planner.validation.ContentPath;
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

    public static List<Violation> check(List<Long> sinnerIndices) {
        return Stream.of(
                        sinnerIndices.size() > MAX_DEPLOYED
                                ? Stream.of(SinnerViolations.tooManyDeployed(DEPLOYMENT_ORDER, sinnerIndices.size()))
                                : Stream.<Violation>empty(),
                        outOfRange(sinnerIndices),
                        repeated(sinnerIndices))
                .flatMap(violations -> violations)
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> outOfRange(List<Long> sinnerIndices) {
        return IntStream.range(0, sinnerIndices.size())
                .filter(position -> !inRange(sinnerIndices.get(position)))
                .mapToObj(position -> SinnerViolations.deploymentOutOfRange(DEPLOYMENT_ORDER.index(position),
                        sinnerIndices.get(position)));
    }

    private static Stream<Violation> repeated(List<Long> sinnerIndices) {
        List<Optional<Long>> deployed = sinnerIndices.stream()
                .map(sinnerIndex -> Optional.of(sinnerIndex).filter(DeploymentOrder::inRange))
                .toList();
        Map<Optional<Long>, Integer> firstPosition = Positions.firstIndexOf(deployed);
        return IntStream.range(0, deployed.size())
                .boxed()
                .flatMap(position -> deployed.get(position)
                        .filter(sinnerIndex -> firstPosition.get(deployed.get(position)) < position)
                        .map(sinnerIndex -> SinnerViolations.deployedTwice(DEPLOYMENT_ORDER.index(position),
                                DEPLOYMENT_ORDER, sinnerIndex))
                        .stream());
    }

    private static boolean inRange(long sinnerIndex) {
        return sinnerIndex >= FIRST_SINNER_INDEX && sinnerIndex <= LAST_SINNER_INDEX;
    }
}
