package org.danteplanner.backend.planner.validation;

public enum JsonKind {
    ARRAY("array"),
    OBJECT("object"),
    STRING("string"),
    STRING_OR_NULL("string or null"),
    INTEGER("integer"),
    NUMBER("number");

    private final String rendered;

    JsonKind(String rendered) {
        this.rendered = rendered;
    }

    String rendered() {
        return rendered;
    }
}
