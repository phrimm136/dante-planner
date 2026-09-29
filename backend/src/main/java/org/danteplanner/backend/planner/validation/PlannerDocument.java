package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.entity.PlannerKeywords;

import java.util.Set;

public final class PlannerDocument {

    private final String json;
    private final boolean migrated;
    private final PlannerContent content;

    PlannerDocument(String json, boolean migrated, PlannerContent content) {
        this.json = json;
        this.migrated = migrated;
        this.content = content;
    }

    public String json() {
        return json;
    }

    public boolean migrated() {
        return migrated;
    }

    public PlannerContent content() {
        return content;
    }

    public Set<String> keywords() {
        return PlannerKeywords.fromSelection(content.selectedKeywords()).asSet();
    }
}
