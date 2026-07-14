package org.danteplanner.backend.auth.token;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.shared.redis.RedisKeyScanner;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lineage-based refresh token rotation with theft detection.
 *
 * <p>Family state lives in the auth Redis keyed by {@code rt:fam:{family_id}}: one hash
 * per family whose fields map each token's {@code jti} to its lifecycle state, plus a
 * {@code __revoked__} marker. A freshly minted token is {@link RotationState#UNUSED_LATEST};
 * presenting it for rotation mints a successor and moves it to {@link RotationState#PENDING}.
 * The successor's first use marks the parent {@link RotationState#RETIRED}. Externalizing state
 * to Redis lets rotation and theft detection be consistent across every pod in a multi-server
 * deployment.</p>
 *
 * <p>Each single-family transition runs as one atomic Lua script (registered via SCRIPT LOAD,
 * invoked via EVALSHA) so the parent transition and successor registration cannot interleave
 * across instances.</p>
 */
@Service
@Slf4j
public class RefreshRotationService {

    /**
     * Deterministic synthesis input for legacy (pre-deploy) refresh tokens that
     * lack {@code jti}/{@code family_id}. Formatted with {@code (userId, issuedAtMs)}
     * so the same legacy token always maps to the same synthesized family and jti,
     * letting retries enter the supersede branch instead of double-admitting.
     */
    private static final String LEGACY_FAMILY_SYNTHESIS_FORMAT = "legacy-family:%d:%d";
    private static final String LEGACY_JTI_SYNTHESIS_FORMAT = "legacy-jti:%d:%d";

    private static final String FAMILY_KEY_PREFIX = "rt:fam:";
    private static final String FAMILY_KEY_PATTERN = FAMILY_KEY_PREFIX + "*";
    private static final String REVOKED_FIELD = "__revoked__";
    private static final String SUCCESSOR_JWT_FIELD_PREFIX = "succjwt:";
    private static final String FIELD_SEPARATOR = "|";
    private static final String ROTATED_RESULT = "ROTATED";
    private static final String THEFT_RESULT = "THEFT";
    private static final String REVOKED_RESULT = "REVOKED";
    private static final String REUSED_RESULT_PREFIX = "REUSED" + FIELD_SEPARATOR;

    static final String METRIC_OUTCOME = "jwt_rotation_outcome_total";
    static final String TAG_OUTCOME = "outcome";

    static final String OUTCOME_ROTATED = "rotated";
    static final String OUTCOME_RETRY_REUSED = "retry_reused";
    static final String OUTCOME_RETRY_SUPERSEDED = "retry_superseded";
    static final String OUTCOME_THEFT_REVOKED = "theft_revoked";
    static final String OUTCOME_LEGACY_ADMITTED = "legacy_admitted";
    static final String OUTCOME_REJECTED_REVOKED_FAMILY = "rejected_revoked_family";
    static final String OUTCOME_REJECTED_INVALID = "rejected_invalid";

    /**
     * Atomic single-family rotation transition (mechanics §2.2).
     *
     * <p>KEYS[1] = family key; ARGV = jti, parentJti (or ""), successorJti,
     * succExpiryMs, nowMs, ttlMs, successorJwt, reuseWindowMs. Returns {@code "REVOKED"}
     * if the family already carries the revocation marker; {@code "THEFT"} (and revokes
     * the whole family) if the presented token is a replay of a spent
     * ({@code RETIRED}/{@code SUPERSEDED}) token; {@code "REUSED|<jwt>"} if the presented
     * token is a {@code PENDING} retry whose stored successor is still the live tip and
     * within the reuse window, so every concurrent retry converges on the same successor;
     * otherwise marks the parent {@code RETIRED}, supersedes a stale successor on a retry
     * outside the window, registers the new successor {@code UNUSED_LATEST}, moves the
     * presented token to {@code PENDING}, refreshes the family TTL, and returns
     * {@code "ROTATED"}.</p>
     *
     * <p>Entries written as {@code USED} before the rename to {@code RETIRED} may survive
     * in Redis for up to one family TTL; the theft check matches both spellings.</p>
     *
     * <p>The successor JWT is memoized as a {@code succjwt:{jti}} field
     * ({@code "mintedAtMs|jwt"}) so a retry can replay the identical cookie; it is
     * deleted as soon as the parent retires, bounding how long a bearer token rests
     * in Redis.</p>
     */
    private static final String ROTATE_SCRIPT = """
            -- KEYS[1] = rt:fam:{F}
            -- ARGV   = jti, parentJti, successorJti, succExpiryMs, nowMs, ttlMs, succJwt, reuseWindowMs
            local fkey = KEYS[1]
            local jti, parent, succ = ARGV[1], ARGV[2], ARGV[3]

            if redis.call('HGET', fkey, '__revoked__') then return 'REVOKED' end

            local cur = redis.call('HGET', fkey, jti)            -- "STATE|succ|exp" or false
            local state = cur and string.match(cur, '^[^|]+') or 'UNUSED_LATEST'

            if state == 'RETIRED' or state == 'USED' or state == 'SUPERSEDED' then
              redis.call('HSET', fkey, '__revoked__', ARGV[5])   -- THEFT: revoke whole family
              return 'THEFT'
            end

            if parent ~= '' then                                 -- retire parent on successor's first use
              local p = redis.call('HGET', fkey, parent)
              if p and string.match(p,'^[^|]+')=='PENDING' then
                redis.call('HSET', fkey, parent, 'RETIRED||'..ARGV[4])
                redis.call('HDEL', fkey, 'succjwt:'..parent)     -- memoized JWT no longer replayable
              end
            end

            if state == 'PENDING' then                           -- retry: reuse the stored successor or supersede it
              local oldSucc = string.match(cur, '|([^|]*)|')
              local stored = redis.call('HGET', fkey, 'succjwt:'..jti)  -- "mintedAtMs|jwt" or false
              if stored and oldSucc and oldSucc ~= '' then
                local mintedAt, jwt = string.match(stored, '^(%d+)|(.+)$')
                local os = redis.call('HGET', fkey, oldSucc)
                if mintedAt and os and string.match(os,'^[^|]+')=='UNUSED_LATEST'
                    and tonumber(ARGV[5]) - tonumber(mintedAt) < tonumber(ARGV[8]) then
                  return 'REUSED|'..jwt                        -- replay: racers converge on one child
                end
              end
              if oldSucc and oldSucc ~= '' then
                redis.call('HSET', fkey, oldSucc, 'SUPERSEDED||'..ARGV[4])
              end
            end

            redis.call('HSET', fkey, succ, 'UNUSED_LATEST||'..ARGV[4])
            redis.call('HSET', fkey, jti,  'PENDING|'..succ..'|'..ARGV[4])
            redis.call('HSET', fkey, 'succjwt:'..jti, ARGV[5]..'|'..ARGV[7])
            redis.call('PEXPIRE', fkey, ARGV[6])                 -- sliding TTL = the cleanup job
            return 'ROTATED'
            """;

    private final StringRedisTemplate authRedisTemplate;
    private final TokenValidator tokenValidator;
    private final TokenGenerator tokenGenerator;
    private final CookieUtils cookieUtils;
    private final JwtProperties jwtProperties;
    private final MeterRegistry meterRegistry;
    private final boolean legacyAdmitEnabled;
    private final long retryReuseWindowMs;

    private final DefaultRedisScript<String> rotateScript;

    public RefreshRotationService(
            StringRedisTemplate authRedisTemplate,
            TokenValidator tokenValidator,
            TokenGenerator tokenGenerator,
            CookieUtils cookieUtils,
            JwtProperties jwtProperties,
            MeterRegistry meterRegistry,
            @Value("${jwt.rotation.legacy-admit-enabled:true}") boolean legacyAdmitEnabled,
            @Value("${jwt.rotation.retry-reuse-window-ms:30000}") long retryReuseWindowMs) {
        this.authRedisTemplate = authRedisTemplate;
        this.tokenValidator = tokenValidator;
        this.tokenGenerator = tokenGenerator;
        this.cookieUtils = cookieUtils;
        this.jwtProperties = jwtProperties;
        this.meterRegistry = meterRegistry;
        this.legacyAdmitEnabled = legacyAdmitEnabled;
        this.retryReuseWindowMs = retryReuseWindowMs;

        this.rotateScript = new DefaultRedisScript<>();
        this.rotateScript.setScriptText(ROTATE_SCRIPT);
        this.rotateScript.setResultType(String.class);
    }

    /**
     * Rotates a refresh token through the lineage state machine.
     *
     * <p>On a successful rotation a fresh successor is minted and written as a
     * {@code Set-Cookie} on {@code response}.</p>
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
        String parentJti = claims.parentJti() != null ? claims.parentJti() : "";

        String successorJwt = tokenGenerator.generateRefreshToken(claims.userId(), familyId, jti);
        TokenClaims successorClaims = tokenValidator.validateToken(successorJwt);
        String successorJti = successorClaims.jti();
        long succExpiryMs = successorClaims.expiration().getTime();
        long nowMs = System.currentTimeMillis();
        long ttlMs = jwtProperties.getRefreshTokenExpiry();

        // Metric labelling only: the Lua returns ROTATED for both a fresh rotation and a
        // legit retry, so classify a retry by the presented jti's pre-call state. The
        // mutation itself stays atomic inside the script.
        boolean retry = !legacy && presentedState(familyId, jti) == RotationState.PENDING;

        String result = authRedisTemplate.execute(
                rotateScript,
                List.of(familyKey(familyId)),
                jti, parentJti, successorJti,
                String.valueOf(succExpiryMs), String.valueOf(nowMs), String.valueOf(ttlMs),
                successorJwt, String.valueOf(retryReuseWindowMs));

        if (result != null && result.startsWith(REUSED_RESULT_PREFIX)) {
            // Concurrent retry: replay the memoized successor so every racer converges
            // on one cookie; the JWT this call optimistically signed is discarded.
            String storedJwt = result.substring(REUSED_RESULT_PREFIX.length());
            TokenClaims storedClaims = tokenValidator.validateToken(storedJwt);
            cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN, storedJwt,
                    jwtProperties.getRefreshTokenExpirySeconds());
            incrementOutcome(OUTCOME_RETRY_REUSED);
            return new RotationResult.Rotated(storedJwt, storedClaims);
        }

        return switch (result) {
            case THEFT_RESULT -> {
                clearAuthCookies(response);
                incrementOutcome(OUTCOME_THEFT_REVOKED);
                yield new RotationResult.Revoked(familyId);
            }
            case REVOKED_RESULT -> {
                clearAuthCookies(response);
                incrementOutcome(OUTCOME_REJECTED_REVOKED_FAMILY);
                yield new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY);
            }
            case ROTATED_RESULT -> {
                cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN, successorJwt,
                        jwtProperties.getRefreshTokenExpirySeconds());
                incrementOutcome(legacy ? OUTCOME_LEGACY_ADMITTED
                        : retry ? OUTCOME_RETRY_SUPERSEDED : OUTCOME_ROTATED);
                yield new RotationResult.Rotated(successorJwt, successorClaims);
            }
            default -> throw new IllegalStateException(
                    "Unexpected rotation transition result: " + result);
        };
    }

    /**
     * Reads the presented token's rotation state within its own family hash, or null if
     * absent. Used to distinguish a retry of an already-rotated parent from a fresh
     * rotation for metric labelling.
     *
     * @param familyId the token's family
     * @param jti      the presented token's jti
     * @return the presented token's state, or null if unknown
     */
    private RotationState presentedState(String familyId, String jti) {
        Object value = authRedisTemplate.opsForHash().get(familyKey(familyId), jti);
        return parseLeadingState(value == null ? null : value.toString());
    }

    /**
     * Recovers the leading {@link RotationState} from a family-hash field value of shape
     * {@code "<STATE>|<succ>|<exp>"}, taking the segment before the first separator.
     *
     * @param fieldValue the raw hash field value, or null
     * @return the leading state, or null if {@code fieldValue} is null
     */
    private RotationState parseLeadingState(String fieldValue) {
        if (fieldValue == null) {
            return null;
        }
        int sep = fieldValue.indexOf(FIELD_SEPARATOR);
        String leading = sep >= 0 ? fieldValue.substring(0, sep) : fieldValue;
        // Entries persisted before the USED -> RETIRED rename survive one family TTL.
        if ("USED".equals(leading)) {
            return RotationState.RETIRED;
        }
        return RotationState.valueOf(leading);
    }

    /**
     * Clears both auth cookies (access and refresh) by setting their max age to 0, used
     * when a rotation attempt is rejected or triggers a theft-driven family revocation.
     *
     * @param response the response to attach the cleared cookies to
     */
    private void clearAuthCookies(HttpServletResponse response) {
        cookieUtils.clearCookie(response, CookieConstants.REFRESH_TOKEN);
        cookieUtils.clearCookie(response, CookieConstants.ACCESS_TOKEN);
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

    /**
     * Records revocation of an entire token family by writing the {@code __revoked__}
     * marker into the family hash.
     *
     * @param familyId the family to revoke
     */
    public void revokeFamily(String familyId) {
        if (familyId == null) {
            return;
        }
        authRedisTemplate.opsForHash().put(
                familyKey(familyId), REVOKED_FIELD, String.valueOf(System.currentTimeMillis()));
        log.info("Revoked refresh token family {}", familyId);
    }

    private void incrementOutcome(String outcome) {
        Counter.builder(METRIC_OUTCOME)
                .tag(TAG_OUTCOME, outcome)
                .register(meterRegistry)
                .increment();
    }

    private String familyKey(String familyId) {
        return FAMILY_KEY_PREFIX + "{" + familyId + "}";
    }

    /**
     * Returns the rotation state for a {@code jti}, or null if unknown. For testing.
     */
    RotationState stateOf(String jti) {
        for (String key : RedisKeyScanner.scanKeys(authRedisTemplate, FAMILY_KEY_PATTERN)) {
            Object value = authRedisTemplate.opsForHash().get(key, jti);
            if (value != null) {
                return parseLeadingState(value.toString());
            }
        }
        return null;
    }

    /**
     * Whether a family has been revoked. For testing.
     */
    boolean isFamilyRevoked(String familyId) {
        return Boolean.TRUE.equals(
                authRedisTemplate.opsForHash().hasKey(familyKey(familyId), REVOKED_FIELD));
    }

    /**
     * Returns the total number of rotation-state entries across all families,
     * excluding revocation markers and memoized successor JWTs. For testing.
     */
    int rotationStateSize() {
        int total = 0;
        for (String key : RedisKeyScanner.scanKeys(authRedisTemplate, FAMILY_KEY_PATTERN)) {
            for (Object field : authRedisTemplate.opsForHash().keys(key)) {
                if (!REVOKED_FIELD.equals(field)
                        && !field.toString().startsWith(SUCCESSOR_JWT_FIELD_PREFIX)) {
                    total++;
                }
            }
        }
        return total;
    }

    /**
     * Deletes all family state. For testing.
     */
    void clear() {
        Set<String> keys = RedisKeyScanner.scanKeys(authRedisTemplate, FAMILY_KEY_PATTERN);
        if (!keys.isEmpty()) {
            authRedisTemplate.delete(keys);
        }
    }
}
