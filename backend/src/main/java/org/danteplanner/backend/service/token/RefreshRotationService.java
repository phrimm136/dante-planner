package org.danteplanner.backend.service.token;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.JwtProperties;
import org.danteplanner.backend.exception.InvalidTokenException;
import org.danteplanner.backend.util.CookieConstants;
import org.danteplanner.backend.util.CookieUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lineage-based refresh token rotation with theft detection.
 *
 * <p>Maintains per-token rotation state keyed by {@code jti} and a per-family
 * revocation map. A freshly minted token is {@link RotationState#UNUSED_LATEST};
 * presenting it for rotation mints a successor and moves it to
 * {@link RotationState#PENDING}. The successor's first use marks the parent
 * {@link RotationState#USED}; a retry of the parent re-mints a fresh successor and
 * marks the prior successor {@link RotationState#SUPERSEDED}. Presenting a
 * {@code USED} or {@code SUPERSEDED} token signals theft and revokes the whole
 * family. Any token whose family is revoked is rejected.</p>
 *
 * <p>All single-key read-modify-write transitions go through
 * {@link ConcurrentHashMap#compute} so concurrent rotation of the same parent is
 * serialized: exactly one transition wins, the other goes through the supersede
 * branch, and both presenters receive a valid successor cookie.</p>
 *
 * <p>Note: single-server, in-memory model mirroring {@link TokenBlacklistService}.
 * Multi-server deployment would migrate both maps to Redis with TTL entries.</p>
 */
@Service
@Slf4j
public class RefreshRotationService {

    /**
     * Map size at which an opportunistic {@link #cleanupExpired()} is triggered from
     * the rotation path. Not a hard cap: entries self-expire with the refresh-token TTL
     * and the hourly {@code @Scheduled} sweep is the primary reclaimer. This threshold
     * only amortizes a sweep into the request path if growth outpaces the schedule
     * between sweeps, bounding the window where stale entries linger under load.
     */
    private static final int CLEANUP_TRIGGER_THRESHOLD = 100_000;

    /**
     * Number of fixed lock stripes. A family is mapped to a stripe by hash, so the
     * lock set is bounded and never evicted; distinct families occasionally share a
     * stripe and serialize, which is safe.
     */
    private static final int FAMILY_LOCK_STRIPES = 256;

    /**
     * Deterministic synthesis input for legacy (pre-deploy) refresh tokens that
     * lack {@code jti}/{@code family_id}. Formatted with {@code (userId, issuedAtMs)}
     * so the same legacy token always maps to the same synthesized family and jti,
     * letting retries enter the supersede branch instead of double-admitting.
     */
    private static final String LEGACY_FAMILY_SYNTHESIS_FORMAT = "legacy-family:%d:%d";
    private static final String LEGACY_JTI_SYNTHESIS_FORMAT = "legacy-jti:%d:%d";

    static final String METRIC_OUTCOME = "jwt_rotation_outcome_total";
    static final String METRIC_STATE_SIZE = "jwt_rotation_state_size";
    static final String METRIC_REVOKED_FAMILIES_SIZE = "jwt_rotation_revoked_families_size";
    static final String TAG_OUTCOME = "outcome";

    static final String OUTCOME_ROTATED = "rotated";
    static final String OUTCOME_RETRY_SUPERSEDED = "retry_superseded";
    static final String OUTCOME_THEFT_REVOKED = "theft_revoked";
    static final String OUTCOME_LEGACY_ADMITTED = "legacy_admitted";
    static final String OUTCOME_REJECTED_REVOKED_FAMILY = "rejected_revoked_family";
    static final String OUTCOME_REJECTED_INVALID = "rejected_invalid";

    private final TokenValidator tokenValidator;
    private final TokenGenerator tokenGenerator;
    private final CookieUtils cookieUtils;
    private final JwtProperties jwtProperties;
    private final MeterRegistry meterRegistry;
    private final boolean legacyAdmitEnabled;

    /**
     * Maps a refresh token's {@code jti} to its rotation bookkeeping entry.
     */
    private final ConcurrentHashMap<String, RotationEntry> rotationState = new ConcurrentHashMap<>();

    /**
     * Maps a revoked {@code family_id} to the epoch-millisecond revocation timestamp.
     */
    private final ConcurrentHashMap<String, Long> revokedFamilies = new ConcurrentHashMap<>();

    /**
     * Fixed lock stripes. A whole lineage is the atomicity unit: a single rotation
     * mutates the parent, the prior successor, and the new successor, all sharing one
     * {@code family_id}. Each family deterministically maps to one stripe, so mutual
     * exclusion per family holds without any lock lifecycle to manage.
     */
    private final Object[] familyLockStripes;

    public RefreshRotationService(
            TokenValidator tokenValidator,
            TokenGenerator tokenGenerator,
            CookieUtils cookieUtils,
            JwtProperties jwtProperties,
            MeterRegistry meterRegistry,
            @Value("${jwt.rotation.legacy-admit-enabled:true}") boolean legacyAdmitEnabled) {
        this.tokenValidator = tokenValidator;
        this.tokenGenerator = tokenGenerator;
        this.cookieUtils = cookieUtils;
        this.jwtProperties = jwtProperties;
        this.meterRegistry = meterRegistry;
        this.legacyAdmitEnabled = legacyAdmitEnabled;

        this.familyLockStripes = new Object[FAMILY_LOCK_STRIPES];
        for (int i = 0; i < FAMILY_LOCK_STRIPES; i++) {
            this.familyLockStripes[i] = new Object();
        }

        meterRegistry.gauge(METRIC_STATE_SIZE, rotationState, ConcurrentHashMap::size);
        meterRegistry.gauge(METRIC_REVOKED_FAMILIES_SIZE, revokedFamilies, ConcurrentHashMap::size);
    }

    /**
     * Rotates a refresh token through the lineage state machine.
     *
     * <p>On a successful rotation a fresh successor is minted and written as a
     * {@code Set-Cookie} on {@code response}. On theft detection or a revoked family
     * the cookies are cleared and a rejecting outcome is returned.</p>
     *
     * @param refreshToken the presented refresh JWT
     * @param response     the response to attach cookies to
     * @return the rotation outcome
     */
    public RotationResult rotate(String refreshToken, HttpServletResponse response) {
        TokenClaims claims;
        try {
            claims = tokenValidator.validateToken(refreshToken);
        } catch (InvalidTokenException e) {
            incrementOutcome(OUTCOME_REJECTED_INVALID);
            return new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID);
        }

        // An access token has null jti+familyId and would otherwise hit the legacy-admit
        // branch and be upgraded to a refresh token; reject it regardless of the flag.
        if (!claims.isRefreshToken()) {
            incrementOutcome(OUTCOME_REJECTED_INVALID);
            return new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID);
        }

        boolean legacy = claims.jti() == null || claims.familyId() == null;
        if (legacy) {
            // Legacy (pre-deploy) tokens lack lineage claims. When admission is
            // enabled, synthesize deterministic jti/family_id so the same legacy
            // token always maps to the same lineage across retries, then proceed
            // through normal rotation; the successor carries proper UUID claims.
            if (!legacyAdmitEnabled) {
                incrementOutcome(OUTCOME_REJECTED_INVALID);
                return new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID);
            }
            claims = admitLegacy(claims);
        }

        String jti = claims.jti();
        String familyId = claims.familyId();

        if (revokedFamilies.containsKey(familyId)) {
            clearAuthCookies(response);
            incrementOutcome(OUTCOME_REJECTED_REVOKED_FAMILY);
            return new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY);
        }

        // The whole lineage is mutated under one monitor so the parent transition, the
        // prior-successor supersede, and the new-successor registration are atomic.
        // ConcurrentHashMap.compute cannot be used here: minting a successor requires
        // writing a second key, and nested same-map mutation throws "Recursive update".
        RotationResult result;
        synchronized (familyLock(familyId)) {
            RotationEntry current = rotationState.get(jti);
            if (current != null && (current.state() == RotationState.USED
                    || current.state() == RotationState.SUPERSEDED)) {
                revokeFamily(familyId);
                clearAuthCookies(response);
                incrementOutcome(OUTCOME_THEFT_REVOKED);
                return new RotationResult.Revoked(familyId);
            }

            markParentUsedIfPending(claims.parentJti(), jti);
            result = performRotation(claims, current, legacy);
        }

        cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN,
                ((RotationResult.Rotated) result).newRefreshJwt(),
                jwtProperties.getRefreshTokenExpirySeconds());

        // Run the opportunistic sweep outside the family lock so a full-map scan never
        // serializes unrelated rotations. Revoked early-returns above skip this by design.
        if (rotationState.size() > CLEANUP_TRIGGER_THRESHOLD) {
            cleanupExpired();
        }
        return result;
    }

    /**
     * Rebuilds claims for a legacy refresh token with deterministically synthesized
     * {@code jti} and {@code family_id}, derived from {@code (userId, issuedAtMs)} so
     * repeated presentations of the same legacy token resolve to the same lineage.
     *
     * @param legacy the validated claims of a legacy token (jti/familyId null)
     * @return claims with synthesized lineage identifiers, all other fields preserved
     */
    private TokenClaims admitLegacy(TokenClaims legacy) {
        long issuedAtMs = legacy.issuedAt().getTime();
        String synthesizedFamilyId = UUID.nameUUIDFromBytes(
                String.format(LEGACY_FAMILY_SYNTHESIS_FORMAT, legacy.userId(), issuedAtMs)
                        .getBytes(StandardCharsets.UTF_8)).toString();
        String synthesizedJti = UUID.nameUUIDFromBytes(
                String.format(LEGACY_JTI_SYNTHESIS_FORMAT, legacy.userId(), issuedAtMs)
                        .getBytes(StandardCharsets.UTF_8)).toString();
        return new TokenClaims(
                legacy.userId(), legacy.email(), legacy.type(), legacy.role(),
                legacy.issuedAt(), legacy.expiration(),
                synthesizedJti, synthesizedFamilyId, legacy.parentJti());
    }

    private Object familyLock(String familyId) {
        return familyLockStripes[Math.floorMod(familyId.hashCode(), FAMILY_LOCK_STRIPES)];
    }

    private void markParentUsedIfPending(String parentJti, String successorJti) {
        if (parentJti == null) {
            return;
        }
        RotationEntry parent = rotationState.get(parentJti);
        if (parent != null && parent.state() == RotationState.PENDING
                && successorJti.equals(parent.successorJti())) {
            rotationState.put(parentJti, new RotationEntry(
                    RotationState.USED, null, null,
                    parent.familyId(), parent.issuedAt(), parent.expiryMs()));
        }
    }

    private RotationResult performRotation(TokenClaims claims, RotationEntry existing, boolean legacy) {
        String jti = claims.jti();
        String familyId = claims.familyId();

        String successorJwt = tokenGenerator.generateRefreshToken(
                claims.userId(), familyId, jti);
        TokenClaims successorClaims = tokenValidator.validateToken(successorJwt);
        String successorJti = successorClaims.jti();

        boolean retry = existing != null && existing.state() == RotationState.PENDING;
        if (retry && existing.successorJti() != null) {
            RotationEntry oldSuccessor = rotationState.get(existing.successorJti());
            if (oldSuccessor != null) {
                rotationState.put(existing.successorJti(), new RotationEntry(
                        RotationState.SUPERSEDED, null, null,
                        oldSuccessor.familyId(), oldSuccessor.issuedAt(), oldSuccessor.expiryMs()));
            }
        }

        rotationState.put(successorJti, new RotationEntry(
                RotationState.UNUSED_LATEST, null, null,
                familyId,
                successorClaims.issuedAt().getTime(),
                successorClaims.expiration().getTime()));

        rotationState.put(jti, new RotationEntry(
                RotationState.PENDING, successorJti, successorJwt,
                familyId,
                existing != null ? existing.issuedAt() : claims.issuedAt().getTime(),
                existing != null ? existing.expiryMs() : claims.expiration().getTime()));

        String outcome = legacy ? OUTCOME_LEGACY_ADMITTED
                : retry ? OUTCOME_RETRY_SUPERSEDED : OUTCOME_ROTATED;
        incrementOutcome(outcome);
        return new RotationResult.Rotated(successorJwt, successorClaims);
    }

    /**
     * Revokes an entire token family. Subsequent presentation of any token in the
     * family is rejected.
     *
     * @param familyId the family to revoke
     */
    public void revokeFamily(String familyId) {
        if (familyId == null) {
            return;
        }
        revokedFamilies.put(familyId, System.currentTimeMillis());
        log.info("Revoked refresh token family {}", familyId);
    }

    private void clearAuthCookies(HttpServletResponse response) {
        cookieUtils.clearCookie(response, CookieConstants.ACCESS_TOKEN);
        cookieUtils.clearCookie(response, CookieConstants.REFRESH_TOKEN);
    }

    private void incrementOutcome(String outcome) {
        Counter.builder(METRIC_OUTCOME)
                .tag(TAG_OUTCOME, outcome)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Removes rotation-state entries past their token expiry and revoked-family
     * entries past the refresh TTL retention window. Lock stripes are fixed and
     * never pruned.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupExpired() {
        long now = System.currentTimeMillis();

        int stateBefore = rotationState.size();
        rotationState.entrySet().removeIf(e -> e.getValue().expiryMs() < now);
        int stateRemoved = stateBefore - rotationState.size();

        long retentionCutoff = now - jwtProperties.getRefreshTokenExpiry();
        int familiesBefore = revokedFamilies.size();
        revokedFamilies.entrySet().removeIf(e -> e.getValue() < retentionCutoff);
        int familiesRemoved = familiesBefore - revokedFamilies.size();

        if (stateRemoved > 0 || familiesRemoved > 0) {
            log.debug("Rotation cleanup: removed {} expired entries ({} remaining), {} stale families ({} remaining)",
                    stateRemoved, rotationState.size(), familiesRemoved, revokedFamilies.size());
        }
    }

    /**
     * Returns the current rotation-state map size. For monitoring and testing.
     */
    public int rotationStateSize() {
        return rotationState.size();
    }

    /**
     * Returns the current revoked-families map size. For monitoring and testing.
     */
    public int revokedFamiliesSize() {
        return revokedFamilies.size();
    }

    /**
     * Seeds a rotation entry directly. For testing cleanup against arbitrary
     * {@code expiryMs} that real sub-second JWT expiry cannot express reliably.
     */
    void putEntryForTest(String jti, RotationEntry entry) {
        rotationState.put(jti, entry);
    }

    /**
     * Returns the rotation state for a {@code jti}, or null if unknown. For testing.
     */
    RotationState stateOf(String jti) {
        RotationEntry entry = rotationState.get(jti);
        return entry != null ? entry.state() : null;
    }

    /**
     * Whether a family has been revoked. For testing.
     */
    boolean isFamilyRevoked(String familyId) {
        return revokedFamilies.containsKey(familyId);
    }

    /**
     * Clears both maps. For testing.
     */
    void clear() {
        rotationState.clear();
        revokedFamilies.clear();
    }
}
