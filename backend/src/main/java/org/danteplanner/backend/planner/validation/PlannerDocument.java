package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.entity.PlannerKeywords;

import java.util.Set;

public final class PlannerDocument {

    private final String json;
    private final boolean migrated;

    PlannerDocument(String json, boolean migrated) {
        this.json = json;
        this.migrated = migrated;
    }

    public String json() {
        return json;
    }

    public boolean migrated() {
        return migrated;
    }

    public Set<String> keywords() {
        return PlannerKeywords.fromContent(json).asSet();
    }
}
