package org.danteplanner.backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * Runtime-mutable feature flag gating lineage-based refresh token rotation.
 *
 * <p>Read from {@code jwt.rotation.lineage-enabled} (default {@code false}), which the deployment
 * supplies, so every pod in a region agrees on the value and a restart preserves it.</p>
 */
@Component
public class LineageRotationFlag {

    private final boolean enabled;

    public LineageRotationFlag(@Value("${jwt.rotation.lineage-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
