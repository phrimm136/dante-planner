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
    void check_WhenIndicesFallOutsideTheSinners_ReportsEachInDocumentOrder() {
        assertThat(DeploymentOrder.check(json("[-1,12,11]"))).containsExactly(
                new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(0),
                        "deploymentOrder[0] value -1 is out of range [0-11]"),
                new Violation(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, DEPLOYMENT_ORDER.index(1),
                        "deploymentOrder[1] value 12 is out of range [0-11]"));
    }
}
