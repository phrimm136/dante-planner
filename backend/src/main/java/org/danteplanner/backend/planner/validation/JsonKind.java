package org.danteplanner.backend.planner.validation;

import java.util.Locale;

public enum JsonKind {
    ARRAY,
    OBJECT,
    STRING,
    INTEGER;

    String rendered() {
        return name().toLowerCase(Locale.ROOT);
    }
}
