package org.danteplanner.backend.integration.consistency;

public enum CoverageTarget {
    GATE_PINS_PRIMARY("gate pins the primary", "gtid.gate", "outcome", "primary"),
    GATE_SERVES_REPLICA("gate serves the replica", "gtid.gate", "outcome", "replica"),
    REPLICA_MISS_PROMOTED("replica miss promoted", "replica_miss_promoted_total", null, null),
    TOMBSTONE_HIT("tombstone hit", "tombstone.hit", "scope", "planner"),
    WITHDRAWAL_MARKER_HIT("withdrawal-marker hit", "tombstone.hit", "scope", "published-planner");

    private final String label;
    private final String meter;
    private final String tagKey;
    private final String tagValue;

    CoverageTarget(String label, String meter, String tagKey, String tagValue) {
        this.label = label;
        this.meter = meter;
        this.tagKey = tagKey;
        this.tagValue = tagValue;
    }

    public String label() {
        return label;
    }

    public String meter() {
        return meter;
    }

    public String[] tags() {
        return tagKey == null ? new String[0] : new String[] {tagKey, tagValue};
    }
}
