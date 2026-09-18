package org.danteplanner.backend.planner.entity;

public enum PublicationChange {

    NONE,

    FIRST_PUBLISH,

    REPUBLISH,

    WITHDRAWN;

    public boolean changed() {
        return this != NONE;
    }
}
