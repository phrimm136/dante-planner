package org.danteplanner.backend.shared.freshness;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.danteplanner.backend.shared.failure.OnUndeclared;
import org.danteplanner.backend.shared.gtid.GtidOrigin;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

public class MarkerStamp {

    public static final String UNDECLARED_COUNTER = "freshness.marker.undeclared";
    public static final String UNDECLARED_MESSAGE =
            "a window committed a write and declared no freshness scope: call FreshnessDeclarations.declare(...) "
                    + "or declareNone(reason) in the service that wrote";

    private final GtidWriteCapture writeCapture;
    private final MarkerStore markerStore;
    private final Counter undeclared;
    private final OnUndeclared onUndeclared;

    public MarkerStamp(GtidWriteCapture writeCapture, MarkerStore markerStore, MeterRegistry meterRegistry,
            OnUndeclared onUndeclared) {
        this.writeCapture = writeCapture;
        this.markerStore = markerStore;
        this.undeclared = Counter.builder(UNDECLARED_COUNTER).register(meterRegistry);
        this.onUndeclared = onUndeclared;
    }

    public void stamp() {
        writeCapture.takeCapture().ifPresent(this::stamp);
    }

    private void stamp(GtidWriteCapture.Capture capture) {
        switch (FreshnessDeclarations.declaration()) {
            case FreshnessDeclaration.Scopes scopes -> markerStore.raise(scopes.scopes(), capture.gtidSet());
            case FreshnessDeclaration.None none -> {
            }
            case FreshnessDeclaration.Undeclared nothingDeclared -> judgeUndeclared(capture);
        }
    }

    private void judgeUndeclared(GtidWriteCapture.Capture capture) {
        switch (capture.origin()) {
            case TRACKER -> {
                switch (onUndeclared) {
                    case FAIL -> throw new IllegalStateException(UNDECLARED_MESSAGE);
                    case COUNT -> undeclared.increment();
                }
            }
            case FALLBACK -> { }
        }
    }
}
