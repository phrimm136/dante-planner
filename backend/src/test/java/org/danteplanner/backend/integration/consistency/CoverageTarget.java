package org.danteplanner.backend.integration.consistency;

import java.util.Locale;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.danteplanner.backend.shared.config.ReadSourcesConfig;

public enum CoverageTarget {
    GATE_PINS_PRIMARY("gate pins the primary", Region.SEOUL, Instrument.COUNTER, "gtid.gate", "outcome", "primary"),
    GATE_SERVES_REPLICA("gate serves the replica", Region.SEOUL, Instrument.COUNTER, "gtid.gate", "outcome",
            "replica"),
    OREGON_READ_FELL_BACK("oregon read fell back", Region.OREGON, Instrument.TIMER, "hikaricp.connections.acquire",
            "pool", "seoul-replica"),
    OREGON_BREAKER_OPENED("oregon breaker opened", Region.OREGON, Instrument.COUNTER,
            ReadSourcesConfig.BREAKER_TRANSITIONS, "to", CircuitBreaker.State.OPEN.name().toLowerCase(Locale.ROOT));

    public enum Instrument {
        COUNTER,
        TIMER
    }

    private final String label;
    private final Region region;
    private final Instrument instrument;
    private final String meter;
    private final String tagKey;
    private final String tagValue;

    CoverageTarget(String label, Region region, Instrument instrument, String meter, String tagKey,
            String tagValue) {
        this.label = label;
        this.region = region;
        this.instrument = instrument;
        this.meter = meter;
        this.tagKey = tagKey;
        this.tagValue = tagValue;
    }

    public String label() {
        return label;
    }

    public Region region() {
        return region;
    }

    public Instrument instrument() {
        return instrument;
    }

    public String meter() {
        return meter;
    }

    public String[] tags() {
        return tagKey == null ? new String[0] : new String[] {tagKey, tagValue};
    }
}
