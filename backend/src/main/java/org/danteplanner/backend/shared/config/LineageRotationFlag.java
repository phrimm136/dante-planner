package org.danteplanner.backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


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
