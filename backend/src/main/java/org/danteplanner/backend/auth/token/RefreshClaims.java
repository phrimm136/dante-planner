package org.danteplanner.backend.auth.token;

import java.util.Date;

public record RefreshClaims(Long userId, Date issuedAt, Date expiration, RefreshLineage lineage) {
}
