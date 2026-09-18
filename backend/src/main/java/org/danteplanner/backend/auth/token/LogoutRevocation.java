package org.danteplanner.backend.auth.token;

import java.util.Date;
import java.util.Objects;

import org.springframework.lang.NonNull;

public sealed interface LogoutRevocation
        permits LogoutRevocation.TokenRevocation, LogoutRevocation.FamilyRevocation {

    record TokenRevocation(@NonNull String token, @NonNull Date expiry) implements LogoutRevocation {

        public TokenRevocation {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(expiry, "expiry");
        }
    }

    record FamilyRevocation(@NonNull String familyId) implements LogoutRevocation {

        public FamilyRevocation {
            Objects.requireNonNull(familyId, "familyId");
        }
    }
}
