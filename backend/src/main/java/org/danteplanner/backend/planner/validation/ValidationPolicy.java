package org.danteplanner.backend.planner.validation;

public enum ValidationPolicy {

    DRAFT(false),

    PUBLISH(true);

    private final boolean requiresPublishableContent;

    ValidationPolicy(boolean requiresPublishableContent) {
        this.requiresPublishableContent = requiresPublishableContent;
    }

    public static ValidationPolicy forPublicationState(boolean published) {
        return published ? PUBLISH : DRAFT;
    }

    public boolean requiresPublishableContent() {
        return requiresPublishableContent;
    }
}
