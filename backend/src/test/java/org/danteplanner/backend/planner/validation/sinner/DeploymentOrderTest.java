package org.danteplanner.backend.planner.validation.sinner;

import com.fasterxml.jackson.databind.node.MissingNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.planner.validation.ContentFixtures.json;

class DeploymentOrderTest {

    private static final ContentPath DEPLOYMENT_ORDER = ContentPath.of("deploymentOrder");

    @Test
    void check_WhenDeploymentOrderIsAbsent_ReportsNothing() {
        assertThat(DeploymentOrder.check(MissingNode.getInstance())).isEmpty();
    }

    @Test
    void check_WhenEveryIndexIsASinner_ReportsNothing() {
        assertThat(DeploymentOrder.check(json("[0,1,2,3,4,5,6,7,8,9,10,11]"))).isEmpty();
    }

    @Test
    void check_WhenDeploymentOrderIsNotAnArray_ReportsItsKind() {
        assertThat(DeploymentOrder.check(json("\"none\""))).containsExactly(new Violation(
                ErrorCode.INVALID_FIELD_TYPE, DEPLOYMENT_ORDER, "Field 'deploymentOrder' must be array, got string \"none\""));
    }

    @Test
    void check_WhenElementsAreNotIntegers_ReportsEachAtItsIndex() {
        assertThat(DeploymentOrder.check(json("[0,\"abc\",1.5]"))).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, DEPLOYMENT_ORDER.index(1),
                        "Field 'deploymentOrder[1]' must be number, got string \"abc\""),
                new Violation(ErrorCode.INVALID_FIELD_TYPE, DEPLOYMENT_ORDER.index(2),
                        "Field 'deploymentOrder[2]' must be number, got number 1.5"));
    }

    @Test
    void check_WhenAnIndexIsAnIntegralValuedFraction_AcceptsItAsItsInteger() {
        assertThat(DeploymentOrder.check(json("[1.0, 2]"))).isEmpty();
    }

    @Test
    void check_WhenASinnerRepeats_ReportsEveryRepeatAfterTheFirst() {
        assertThat(DeploymentOrder.check(json("[1,2,1,1]"))).containsExactly(
                new Violation(ErrorCode.DUPLICATE_DEPLOYMENT, DEPLOYMENT_ORDER.index(2),
                        "Duplicate value '1' in deploymentOrder"),
                new Violation(ErrorCode.DUPLICATE_DEPLOYMENT, DEPLOYMENT_ORDER.index(3),
                        "Duplicate value '1' in deploymentOrder"));
    }

    @Test
    void check_WhenThirteenSinnersAreDeployed_ReportsTheCountBesideTheRepeat() {
        assertThat(DeploymentOrder.check(json("[0,1,2,3,4,5,6,7,8,9,10,11,0]"))).containsExactly(
                new Violation(ErrorCode.EXCESS_DEPLOYMENT, DEPLOYMENT_ORDER,
                        "deploymentOrder count value 13 is out of range [0-12]"),
                new Violation(ErrorCode.DUPLICATE_DEPLOYMENT, DEPLOYMENT_ORDER.index(12),
                        "Duplicate value '0' in deploymentOrder"));
    }

    @Test
    void check_WhenThirteenDistinctEntriesIncludeOutOfRangeOnes_ReportsTheCountAndEachOutOfRangeEntry() {
        assertThat(DeploymentOrder.check(json("[0,1,2,3,4,5,6,7,8,9,10,12,13]"))).containsExactly(
                new Violation(ErrorCode.EXCESS_DEPLOYMENT, DEPLOYMENT_ORDER,
                        "deploymentOrder count value 13 is out of range [0-12]"),
                new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(11),
                        "deploymentOrder[11] value 12 is out of range [0-11]"),
                new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(12),
                        "deploymentOrder[12] value 13 is out of range [0-11]"));
    }

    @Test
    void check_WhenAnOutOfRangeIndexRepeats_ReportsOnlyTheRange() {
        assertThat(DeploymentOrder.check(json("[12,12]"))).extracting(Violation::code)
                .containsExactly(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, ErrorCode.OUT_OF_RANGE_DEPLOYMENT);
    }

    @Test
    void check_WhenIndicesFallOutsideTheSinners_ReportsEachInDocumentOrder() {
        assertThat(DeploymentOrder.check(json("[-1,12,11]"))).containsExactly(
                new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(0),
                        "deploymentOrder[0] value -1 is out of range [0-11]"),
                new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(1),
                        "deploymentOrder[1] value 12 is out of range [0-11]"));
    }
}
