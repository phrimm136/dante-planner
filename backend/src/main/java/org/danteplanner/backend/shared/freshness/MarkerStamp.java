package org.danteplanner.backend.shared.freshness;

import java.util.Optional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

public class MarkerStamp {

    public static final String UNDECLARED_COUNTER = "freshness.marker.undeclared";
    public static final String UNDECLARED_MESSAGE =
            "a window committed a write and declared no freshness scope: call FreshnessDeclarations.declare(...) "
                    + "or declareNone(reason) in the service that wrote";

    private final GtidWriteCapture writeCapture;
    private final MarkerStore markerStore;
    private final Counter undeclared;
    private final boolean failFast;

    public MarkerStamp(GtidWriteCapture writeCapture, MarkerStore markerStore, MeterRegistry meterRegistry,
            boolean failFast) {
        this.writeCapture = writeCapture;
        this.markerStore = markerStore;
        this.undeclared = Counter.builder(UNDECLARED_COUNTER).register(meterRegistry);
        this.failFast = failFast;
    }

    public Optional<String> stamp() {
        Optional<GtidWriteCapture.Capture> taken = writeCapture.takeCapture();
        if (taken.isEmpty()) {
            return Optional.empty();
        }
        GtidWriteCapture.Capture capture = taken.get();
        switch (FreshnessDeclarations.declaration()) {
            case FreshnessDeclaration.Scopes scopes -> markerStore.raise(scopes.scopes(), capture.gtidSet());
            case FreshnessDeclaration.None none -> {
            }
            case FreshnessDeclaration.Undeclared nothingDeclared -> judgeUndeclared(capture);
        }
        return Optional.of(capture.gtidSet());
    }

    private void judgeUndeclared(GtidWriteCapture.Capture capture) {
        if (!capture.tracked()) {
            return;
        }
        if (failFast) {
            throw new IllegalStateException(UNDECLARED_MESSAGE);
        }
        undeclared.increment();
    }
}
