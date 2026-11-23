package org.danteplanner.backend.auth.token;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.shared.redis.AuthWrites;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * <p>Family state lives in the auth Redis keyed by {@code rt:fam:{family_id}}: one hash
 * per family whose fields map each token's {@code jti} to its lifecycle state, plus a
 * {@code __revoked__} marker.</p>
 *
 * <p>Each single-family transition runs as one atomic Lua script (registered via SCRIPT LOAD,
 * invoked via EVALSHA) so the parent transition and successor registration cannot interleave
 * across instances.</p>
 */
@Service
@Slf4j
public class RefreshRotationService {

    static final String FAMILY_KEY_PREFIX = "rt:fam:";
    static final String REVOKED_FIELD = "__revoked__";
    private static final String FIELD_SEPARATOR = "|";
    private static final String REUSED_RESULT_PREFIX = ScriptResult.REUSED.name() + FIELD_SEPARATOR;
    private static final String UNEXPECTED_RESULT_MESSAGE = "Unexpected rotation transition result: ";
    private static final String NO_PARENT = "";

    static final String METRIC_OUTCOME = "jwt_rotation_outcome_total";
    static final String TAG_OUTCOME = "outcome";

    static final String OUTCOME_ROTATED = "rotated";
    static final String OUTCOME_RETRY_REUSED = "retry_reused";
    static final String OUTCOME_RETRY_SUPERSEDED = "retry_superseded";
    static final String OUTCOME_THEFT_REVOKED = "theft_revoked";
    static final String OUTCOME_REJECTED_REVOKED_FAMILY = "rejected_revoked_family";
    static final String OUTCOME_REJECTED_USER_INVALIDATED = "rejected_user_invalidated";
    static final String OUTCOME_REJECTED_INVALID = "rejected_invalid";

    /**
     * <p>KEYS[1] = family key, KEYS[2] = user-invalidation key; ARGV = jti, parentJti (or ""),
     * successorJti, succExpiryMs, nowMs, ttlMs, successorJwt, reuseWindowMs, presentedIssuedAtMs.
     * They carry no common hash tag,
     * so this requires a non-clustered Redis. Returns {@code "INVALIDATED"} if the presented
     * token predates the user's invalidation stamp; {@code "REVOKED"}
     * if the family already carries the revocation marker; {@code "THEFT"} (and revokes
     * the whole family) if the presented token is a replay of a spent
     * ({@code RETIRED}/{@code SUPERSEDED}) token; {@code "REUSED|<jwt>"} if the presented
     * token is a {@code PENDING} retry whose stored successor is still the live tip and
     * within the reuse window, so every concurrent retry converges on the same successor;
     * otherwise marks the parent {@code RETIRED}, supersedes a stale successor on a retry
     * outside the window, registers the new successor {@code UNUSED_LATEST}, moves the
     * presented token to {@code PENDING}, refreshes the family TTL, and returns
     * {@code "SUPERSEDED"} when the presented token was a retry, {@code "ROTATED"} otherwise.</p>
     *
     * <p>The successor JWT is memoized as a {@code succjwt:{jti}} field
     * ({@code "mintedAtMs|jwt"}) so a retry can replay the identical cookie; it is
     * deleted as soon as the parent retires, bounding how long a bearer token rests
     * in Redis.</p>
     */
    private static final String ROTATE_SCRIPT = """
            -- KEYS = rt:fam:{F}, uinv:{user}
            -- ARGV = jti, parentJti, successorJti, succExpiryMs, nowMs, ttlMs, succJwt,
            --        reuseWindowMs, presentedIssuedAtMs
            local fkey, ikey = KEYS[1], KEYS[2]
            local jti, parent, succ = ARGV[1], ARGV[2], ARGV[3]

            local inv = redis.call('GET', ikey)
            if inv and tonumber(ARGV[9]) < tonumber(inv) then return 'INVALIDATED' end

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

            local outcome = 'ROTATED'
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
              outcome = 'SUPERSEDED'
            end

            redis.call('HSET', fkey, succ, 'UNUSED_LATEST||'..ARGV[4])
            redis.call('HSET', fkey, jti,  'PENDING|'..succ..'|'..ARGV[4])
            redis.call('HSET', fkey, 'succjwt:'..jti, ARGV[5]..'|'..ARGV[7])
            redis.call('PEXPIRE', fkey, ARGV[6])                 -- sliding TTL = the cleanup job
            return outcome
            """;

    private final AuthWrites authWrites;
    private final TokenValidator tokenValidator;
    private final TokenGenerator tokenGenerator;
    private final JwtProperties jwtProperties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final long retryReuseWindowMs;

    private final DefaultRedisScript<String> rotateScript;

    public RefreshRotationService(
            AuthWrites authWrites,
            TokenValidator tokenValidator,
            TokenGenerator tokenGenerator,
            JwtProperties jwtProperties,
            MeterRegistry meterRegistry,
            Clock clock,
            @Value("${jwt.rotation.retry-reuse-window-ms:30000}") long retryReuseWindowMs) {
        this.authWrites = authWrites;
        this.tokenValidator = tokenValidator;
        this.tokenGenerator = tokenGenerator;
        this.jwtProperties = jwtProperties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.retryReuseWindowMs = retryReuseWindowMs;

        this.rotateScript = new DefaultRedisScript<>();
        this.rotateScript.setScriptText(ROTATE_SCRIPT);
        this.rotateScript.setResultType(String.class);
    }

    public RotationOutcome rotate(String refreshToken) {
        RefreshClaims claims;
        try {
            claims = tokenValidator.validateRefreshToken(refreshToken);
        } catch (InvalidTokenException e) {
            return rejectInvalid();
        }

        return rotatePresented(claims);
    }

    private RotationOutcome rotatePresented(RefreshClaims claims) {
        String jti = claims.lineage().jti();
        String familyId = claims.lineage().familyId();

        String successorJwt = tokenGenerator.generateRefreshToken(claims.userId(), familyId, jti);
        RefreshClaims successorClaims = tokenValidator.validateRefreshToken(successorJwt);
        String successorJti = successorClaims.lineage().jti();
        long succExpiryMs = successorClaims.expiration().getTime();
        long nowMs = clock.millis();
        long ttlMs = jwtProperties.getRefreshTokenExpiry();

        List<String> keys = List.of(familyKey(familyId), userInvalidationKey(claims.userId()));
        String presentedIssuedAtMs = String.valueOf(claims.issuedAt().getTime());
        String result = authWrites.write(store -> store.execute(
                rotateScript,
                keys,
                jti, parentJtiOf(claims.lineage()), successorJti,
                String.valueOf(succExpiryMs), String.valueOf(nowMs), String.valueOf(ttlMs),
                successorJwt, String.valueOf(retryReuseWindowMs),
                presentedIssuedAtMs));

        return switch (ScriptResult.of(result)) {
            case REUSED -> {
                String storedJwt = result.substring(REUSED_RESULT_PREFIX.length());
                RefreshClaims storedClaims = tokenValidator.validateRefreshToken(storedJwt);
                incrementOutcome(OUTCOME_RETRY_REUSED);
                yield new RotationOutcome.Rotated(storedJwt, storedClaims);
            }
            case THEFT -> {
                incrementOutcome(OUTCOME_THEFT_REVOKED);
                yield new RotationOutcome.Revoked(familyId);
            }
            case REVOKED -> {
                incrementOutcome(OUTCOME_REJECTED_REVOKED_FAMILY);
                yield new RotationOutcome.Rejected(RotationOutcome.Rejected.Reason.REVOKED_FAMILY);
            }
            case INVALIDATED -> {
                incrementOutcome(OUTCOME_REJECTED_USER_INVALIDATED);
                yield new RotationOutcome.Rejected(RotationOutcome.Rejected.Reason.REVOKED_FAMILY);
            }
            case ROTATED -> {
                incrementOutcome(OUTCOME_ROTATED);
                yield new RotationOutcome.Rotated(successorJwt, successorClaims);
            }
            case SUPERSEDED -> {
                incrementOutcome(OUTCOME_RETRY_SUPERSEDED);
                yield new RotationOutcome.Rotated(successorJwt, successorClaims);
            }
        };
    }

    private static String parentJtiOf(RefreshLineage lineage) {
        return switch (lineage) {
            case RefreshLineage.Root root -> NO_PARENT;
            case RefreshLineage.Child child -> child.parentJti();
        };
    }

    private RotationOutcome rejectInvalid() {
        incrementOutcome(OUTCOME_REJECTED_INVALID);
        return new RotationOutcome.Rejected(RotationOutcome.Rejected.Reason.INVALID);
    }

    /**
     * Each constant is spelled
     * exactly as the script returns it; {@link #REUSED} carries the memoized successor JWT
     * behind a separator.
     */
    private enum ScriptResult {
        ROTATED,
        SUPERSEDED,
        REUSED,
        THEFT,
        REVOKED,
        INVALIDATED;

        static ScriptResult of(String result) {
            if (result != null) {
                if (result.startsWith(REUSED_RESULT_PREFIX)) {
                    return REUSED;
                }
                for (ScriptResult candidate : values()) {
                    if (candidate.name().equals(result)) {
                        return candidate;
                    }
                }
            }
            throw new IllegalStateException(UNEXPECTED_RESULT_MESSAGE + result);
        }
    }

    public void revokeFamily(String familyId) {
        if (familyId == null) {
            return;
        }
        String key = familyKey(familyId);
        authWrites.writeWithoutResult(store -> {
            store.opsForHash().put(key, REVOKED_FIELD, String.valueOf(clock.millis()));
            // Revoking a family that never rotated creates the hash here, where the rotation
            // script's sliding PEXPIRE has not run and would not run again.
            store.expire(key, Duration.ofMillis(jwtProperties.getRefreshTokenExpiry()));
        });
        log.info("Revoked refresh token family {}", familyId);
    }

    private void incrementOutcome(String outcome) {
        Counter.builder(METRIC_OUTCOME)
                .tag(TAG_OUTCOME, outcome)
                .register(meterRegistry)
                .increment();
    }

    static String familyKey(String familyId) {
        return FAMILY_KEY_PREFIX + "{" + familyId + "}";
    }

    private String userInvalidationKey(Long userId) {
        return TokenBlacklistService.USER_INVALIDATION_KEY_PREFIX + userId;
    }
}
