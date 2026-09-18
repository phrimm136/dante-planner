package org.danteplanner.backend.shared.sanitize;

import org.danteplanner.backend.shared.util.HtmlSanitizer;
import org.danteplanner.backend.shared.util.PlannerContentSanitizer;

public enum SanitizerKind {

    PLAIN {
        @Override
        public String apply(String value) {
            return HtmlSanitizer.sanitize(value);
        }
    },

    PLANNER_CONTENT {
        @Override
        public String apply(String value) {
            return PlannerContentSanitizer.sanitize(value);
        }
    };

    public abstract String apply(String value);
}
