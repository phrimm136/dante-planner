package org.danteplanner.backend.planner.validation;

/**
 * How completely a planner document must be filled in to pass validation.
 *
 * <p>Both policies apply the same structural and reference rules. They differ on whether an
 * unfinished floor is tolerated: a draft may still be missing its theme packs, while a document
 * that is publicly visible may not.</p>
 */
public enum ValidationPolicy {

    /** Save or draft: a floor may omit its theme pack. */
    DRAFT(false),

    /** Publish: every floor names a theme pack that exists, at a difficulty its category allows. */
    PUBLISH(true);

    private final boolean requiresPublishableContent;

    ValidationPolicy(boolean requiresPublishableContent) {
        this.requiresPublishableContent = requiresPublishableContent;
    }

    /**
     * The policy a planner in the given publication state is held to.
     *
     * @param published the planner's publication state
     * @return {@link #PUBLISH} for a published planner, {@link #DRAFT} otherwise
     */
    public static ValidationPolicy forPublicationState(boolean published) {
        return published ? PUBLISH : DRAFT;
    }

    /**
     * Whether a floor must name a theme pack this policy can resolve.
     *
     * @return true when unfinished floors are rejected
     */
    public boolean requiresPublishableContent() {
        return requiresPublishableContent;
    }
}
