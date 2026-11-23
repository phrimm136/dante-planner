package org.danteplanner.backend.auth.token;

import org.jspecify.annotations.Nullable;

public sealed interface RefreshLineage {

    String jti();

    String familyId();

    record Root(String jti, String familyId) implements RefreshLineage {
    }

    record Child(String jti, String familyId, String parentJti) implements RefreshLineage {
    }

    static RefreshLineage of(String jti, String familyId, @Nullable String parentJti) {
        if (parentJti == null) {
            return new Root(jti, familyId);
        }
        return new Child(jti, familyId, parentJti);
    }
}
