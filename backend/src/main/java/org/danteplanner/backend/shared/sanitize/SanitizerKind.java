package org.danteplanner.backend.shared.sanitize;

import org.danteplanner.backend.shared.util.HtmlSanitizer;
import org.danteplanner.backend.shared.util.PlannerContentSanitizer;

/**
 * The transformations a request-DTO property may declare through {@link Sanitized}.
 *
 * <p>Each kind names a field shape rather than a sanitizer: the shape is what an author of a new
 * request DTO knows, and the mapping to a sanitizer is this enum's job.</p>
 */
public enum SanitizerKind {

    /**
     * A plain-text field. Every HTML tag is removed and only the text content survives.
     */
    PLAIN {
        @Override
        public String apply(String value) {
            return HtmlSanitizer.sanitize(value);
        }
    },

    /**
     * A planner document. The URLs of the Tiptap JSON carried under {@code sectionNotes} are
     * checked against the protocol allowlist and dangerous ones are replaced.
     */
    PLANNER_CONTENT {
        @Override
        public String apply(String value) {
            return PlannerContentSanitizer.sanitize(value);
        }
    };

    /**
     * Applies this kind's transformation to a value as it arrived on the request.
     *
     * @param value the raw value, which may be null or blank
     * @return the sanitized value
     */
    public abstract String apply(String value);
}
