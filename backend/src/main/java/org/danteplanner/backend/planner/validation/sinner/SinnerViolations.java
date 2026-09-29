package org.danteplanner.backend.planner.validation.sinner;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

import java.math.BigInteger;

final class SinnerViolations {

    private static final String OBJECT = "object";
    private static final String INTEGER = "integer";
    private static final int NO_ENTRIES = 0;

    private SinnerViolations() {
    }

    static Violation keyNotANumber(ContentPath containerPath, String key) {
        return Violation.wrongType(ErrorCode.INVALID_FIELD_TYPE, containerPath.key(key),
                String.format("%s key '%s'", containerPath.render(), key), INTEGER);
    }

    static Violation keyOutOfRange(ContentPath containerPath, String key, BigInteger sinner) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_SINNER, containerPath.key(key),
                String.format("%s key", containerPath.render()), sinner.toString(),
                SinnerKey.FIRST_SINNER, SinnerKey.LAST_SINNER);
    }

    static Violation duplicateSinner(ContentPath containerPath, SinnerKey sinnerKey) {
        return new Violation(ErrorCode.DUPLICATE_SINNER, containerPath.key(sinnerKey.raw()),
                String.format("%s names sinner %s under more than one key", containerPath.render(), sinnerKey.padded()));
    }

    static Violation sinnerNotAnObject(ContentPath path) {
        return Violation.wrongType(ErrorCode.INVALID_FIELD_TYPE, path, path.render(), OBJECT);
    }

    static Violation deploymentOutOfRange(ContentPath path, int sinnerIndex) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, path, String.valueOf(sinnerIndex),
                DeploymentOrder.FIRST_SINNER_INDEX, DeploymentOrder.LAST_SINNER_INDEX);
    }

    static Violation tooManyDeployed(ContentPath path, int count) {
        return Violation.valueOutOfRange(ErrorCode.EXCESS_DEPLOYMENT, path, String.format("%s count", path.render()),
                String.valueOf(count), NO_ENTRIES, DeploymentOrder.MAX_DEPLOYED);
    }

    static Violation deployedTwice(ContentPath path, ContentPath deploymentOrder, int sinnerIndex) {
        return Violation.duplicateValue(ErrorCode.DUPLICATE_DEPLOYMENT, path, deploymentOrder.render(),
                String.valueOf(sinnerIndex));
    }
}
