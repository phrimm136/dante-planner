package org.danteplanner.backend.integration;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.io.IOException;

/**
 * Harness control over the Toxiproxy profiles on the app→primary path.
 *
 * <p>{@link #applyWan()} attaches the {@code wan} latency toxic (a fixed 130ms delay on the
 * app→primary direction); {@link #removeWan()} detaches it, leaving the proxy toxic-free. The
 * 130ms is an internal performance-simulation constant of the toxic, not a tunable API
 * parameter — the public methods carry no duration argument (INV4).</p>
 *
 * <p>{@link #applyPartition()} attaches the {@code partition} timeout toxic (a black-hole on the
 * app→primary direction that holds the connection open without forwarding, modeling peering loss);
 * {@link #removePartition()} detaches it. The timeout value is likewise an internal fault-fixture
 * constant, not an API parameter (INV4).</p>
 */
final class ToxiproxyControl {

    private static final String WAN_TOXIC_NAME = "wan";
    private static final long WAN_LATENCY_MILLIS = 130;

    private static final String PARTITION_TOXIC_NAME = "partition";
    private static final long PARTITION_TIMEOUT_MILLIS = 0;

    private final Proxy appToPrimaryProxy;

    ToxiproxyControl(Proxy appToPrimaryProxy) {
        this.appToPrimaryProxy = appToPrimaryProxy;
    }

    void applyWan() throws IOException {
        appToPrimaryProxy.toxics().latency(WAN_TOXIC_NAME, ToxicDirection.UPSTREAM, WAN_LATENCY_MILLIS);
    }

    void removeWan() throws IOException {
        clearToxics();
    }

    void applyPartition() throws IOException {
        appToPrimaryProxy.toxics().timeout(PARTITION_TOXIC_NAME, ToxicDirection.UPSTREAM, PARTITION_TIMEOUT_MILLIS);
    }

    void removePartition() throws IOException {
        clearToxics();
    }

    Proxy appToPrimaryProxy() {
        return appToPrimaryProxy;
    }

    private void clearToxics() throws IOException {
        for (Toxic toxic : appToPrimaryProxy.toxics().getAll()) {
            toxic.remove();
        }
    }
}
