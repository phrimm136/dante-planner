package org.danteplanner.backend.auth.token;

import java.util.Date;

import org.springframework.util.Assert;

import org.jspecify.annotations.Nullable;

public sealed interface RefreshLineage {

    record Legacy(long issuedAtMs) implements RefreshLineage {
    }

    record Root(String jti, String familyId) implements RefreshLineage {
    }

    record Child(String jti, String familyId, String parentJti) implements RefreshLineage {
    }

    static RefreshLineage of(
            @Nullable String jti, @Nullable String familyId, @Nullable String parentJti, Date issuedAt) {
        Assert.notNull(issuedAt, "issuedAt must not be null");
        if (jti == null || familyId == null) {
            return new Legacy(issuedAt.getTime());
        }
        if (parentJti == null) {
            return new Root(jti, familyId);
        }
        return new Child(jti, familyId, parentJti);
    }
}
