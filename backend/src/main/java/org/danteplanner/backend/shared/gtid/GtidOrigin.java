package org.danteplanner.backend.shared.gtid;

public enum GtidOrigin {
    TRACKER("tracker"),
    FALLBACK("fallback");

    private final String tag;

    GtidOrigin(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    GtidOrigin joinedWith(GtidOrigin other) {
        return switch (this) {
            case TRACKER -> TRACKER;
            case FALLBACK -> other;
        };
    }
}
