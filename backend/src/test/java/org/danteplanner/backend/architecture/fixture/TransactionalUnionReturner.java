package org.danteplanner.backend.architecture.fixture;

import org.danteplanner.backend.shared.failure.FailureUnion;
import org.springframework.transaction.annotation.Transactional;

/**
 * A transactional method handing a failure union back to its caller, held so that the rule
 * forbidding it can be shown rejecting something.
 *
 * <p>Lives in test sources, which every rule scanning production code excludes, and carries no
 * stereotype annotation, so nothing component-scans it into a running context.</p>
 */
public class TransactionalUnionReturner {

    /** The violation itself: a marked union declared as the return type of a transactional method. */
    @Transactional
    public Outcome settle() {
        return new Outcome.Refused("");
    }

    /**
     * A union standing in for a real one; only its marker and its sealed closure matter to the
     * rule.
     */
    public sealed interface Outcome extends FailureUnion permits Outcome.Settled, Outcome.Refused {

        /** The operation completed. */
        record Settled() implements Outcome {
        }

        /** The operation declined, naming why. */
        record Refused(String reason) implements Outcome {
        }
    }
}
