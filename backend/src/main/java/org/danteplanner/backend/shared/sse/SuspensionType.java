package org.danteplanner.backend.shared.sse;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kind of suspension an account-suspension SSE event reports.
 *
 * <p>Constant names follow {@link org.danteplanner.backend.user.entity.RestrictionState}; the wire
 * values are the browser's own spelling, which differs for {@link #TIMED_OUT}.</p>
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
