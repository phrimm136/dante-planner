package org.danteplanner.backend.planner.validation;

/**
 * What the optimistic-locking check decided a write should do.
 */
public enum WriteArbitration {

    /** Apply the write and bump the stored version. */
    WRITE,

    /** Answer with the stored version, writing nothing. */
    ACK_NO_OP
}
