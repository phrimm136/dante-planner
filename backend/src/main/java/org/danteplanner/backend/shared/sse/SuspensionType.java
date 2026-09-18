package org.danteplanner.backend.shared.sse;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The wire values are the browser's own spelling, which differs for {@link #TIMED_OUT}.
 */
public enum SuspensionType {

    BAN("BAN"),
    TIMED_OUT("TIMEOUT");

    private final String value;

    SuspensionType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
