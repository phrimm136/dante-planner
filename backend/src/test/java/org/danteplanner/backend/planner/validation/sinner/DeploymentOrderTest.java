package org.danteplanner.backend.planner.validation.sinner;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentOrderTest {

    private static final ContentPath DEPLOYMENT_ORDER = ContentPath.of("deploymentOrder");

    @Test
    void check_WhenDeploymentOrderIsEmpty_ReportsNothing() {
        assertThat(DeploymentOrder.check(List.of())).isEmpty();
    }

    @Test
    void check_WhenEveryIndexIsASinner_ReportsNothing() {
        assertThat(DeploymentOrder.check(List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L))).isEmpty();
    }

    @Test
    void check_WhenASinnerRepeats_ReportsEveryRepeatAfterTheFirst() {
        assertThat(DeploymentOrder.check(List.of(1L, 2L, 1L, 1L))).containsExactly(
                new Violation(ErrorCode.DUPLICATE_DEPLOYMENT, DEPLOYMENT_ORDER.index(2),
                        "Duplicate value '1' in deploymentOrder"),
                new Violation(ErrorCode.DUPLICATE_DEPLOYMENT, DEPLOYMENT_ORDER.index(3),
                        "Duplicate value '1' in deploymentOrder"));
    }

    @Test
    void check_WhenThirteenSinnersAreDeployed_ReportsTheCountBesideTheRepeat() {
        assertThat(DeploymentOrder.check(List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 0L)))
                .containsExactly(
                        new Violation(ErrorCode.EXCESS_DEPLOYMENT, DEPLOYMENT_ORDER,
                                "deploymentOrder count value 13 is out of range [0-12]"),
                        new Violation(ErrorCode.DUPLICATE_DEPLOYMENT, DEPLOYMENT_ORDER.index(12),
                                "Duplicate value '0' in deploymentOrder"));
    }

    @Test
    void check_WhenThirteenDistinctEntriesIncludeOutOfRangeOnes_ReportsTheCountAndEachOutOfRangeEntry() {
        assertThat(DeploymentOrder.check(List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 12L, 13L)))
                .containsExactly(
                        new Violation(ErrorCode.EXCESS_DEPLOYMENT, DEPLOYMENT_ORDER,
                                "deploymentOrder count value 13 is out of range [0-12]"),
                        new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(11),
                                "deploymentOrder[11] value 12 is out of range [0-11]"),
                        new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(12),
                                "deploymentOrder[12] value 13 is out of range [0-11]"));
    }

    @Test
    void check_WhenAnOutOfRangeIndexRepeats_ReportsOnlyTheRange() {
        assertThat(DeploymentOrder.check(List.of(12L, 12L))).extracting(Violation::code)
                .containsExactly(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, ErrorCode.OUT_OF_RANGE_DEPLOYMENT);
    }

    @Test
    void check_WhenIndicesFallOutsideTheSinners_ReportsEachInDocumentOrder() {
        assertThat(DeploymentOrder.check(List.of(-1L, 12L, 11L))).containsExactly(
                new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(0),
                        "deploymentOrder[0] value -1 is out of range [0-11]"),
                new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(1),
                        "deploymentOrder[1] value 12 is out of range [0-11]"));
    }
}
