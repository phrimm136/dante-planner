package org.danteplanner.backend.shared.freshness;

import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

public class MarkerDispatchStamp implements DispatchStamp {

    private final GtidWriteCapture writeCapture;
    private final MarkerStamp markerStamp;

    public MarkerDispatchStamp(GtidWriteCapture writeCapture, MarkerStamp markerStamp) {
        this.writeCapture = writeCapture;
        this.markerStamp = markerStamp;
    }

    @Override
    public void around(Runnable dispatch) {
        if (FreshnessDeclarations.isWindowOpen()) {
            throw new IllegalStateException("a dispatch window cannot open inside another stamp window");
        }
        writeCapture.begin();
        FreshnessDeclarations.begin();
        ChosenReadSource.clear();
        ReadSourceSelector.clearSkips();
        try {
            dispatch.run();
        } finally {
            writeCapture.clear();
            FreshnessDeclarations.clear();
            ChosenReadSource.clear();
            ReadSourceSelector.clearSkips();
        }
    }

    @Override
    public void raise() {
        markerStamp.stamp();
    }
}
