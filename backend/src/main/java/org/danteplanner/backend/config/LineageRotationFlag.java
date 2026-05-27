package org.danteplanner.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime-mutable feature flag gating lineage-based refresh token rotation.
 *
 * <p>Seeded at startup from {@code jwt.rotation.lineage-enabled} (default {@code false})
 * and flippable at runtime via {@code POST /api/internal/feature-flags/lineage-rotation}.
 * Backed by an {@link AtomicBoolean} for cross-thread visibility: the flag is read on the
 * authentication hot path from request threads while the toggle is set from a separate
 * internal-API request thread.</p>
 */
@Component
public class LineageRotationFlag {

    private final AtomicBoolean enabled;

    public LineageRotationFlag(@Value("${jwt.rotation.lineage-enabled:false}") boolean initial) {
        this.enabled = new AtomicBoolean(initial);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
