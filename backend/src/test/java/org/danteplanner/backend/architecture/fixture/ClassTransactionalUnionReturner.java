package org.danteplanner.backend.architecture.fixture;

import org.springframework.transaction.annotation.Transactional;

/**
 * The same violation attached at class level rather than to the method.
 *
 * <p>The rule treats a method as transactional when either it or its owner carries the annotation.
 * No class in main sources carries it, so without this fixture the owner half of that condition
 * would be live code with nothing exercising it.</p>
 */
@Transactional
public class ClassTransactionalUnionReturner {

    /** Transactional by the class annotation alone, carrying none of its own. */
    public TransactionalUnionReturner.Outcome settle() {
        return new TransactionalUnionReturner.Outcome.Settled();
    }
}
