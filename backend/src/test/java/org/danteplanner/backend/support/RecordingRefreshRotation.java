package org.danteplanner.backend.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RotationResult;
import org.danteplanner.backend.auth.token.RotationState;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.TokenClaims;

public class RecordingRefreshRotation extends RefreshRotationService {

    public static final boolean DEFAULT_LEGACY_ADMIT_ENABLED = true;
    public static final long DEFAULT_RETRY_REUSE_WINDOW_MS = 30_000L;

    public enum Operation {
        ROTATE,
        REVOKE_FAMILY
    }

    public sealed interface Call permits Rotation, FamilyRevoked, Failed {
    }

    public record Rotation(String presented, RotationResult result) implements Call {
    }

    public record FamilyRevoked(String familyId) implements Call {
    }

    public record Failed(Operation operation, RuntimeException failure) implements Call {
    }

    private record Entry(RotationState state, String successorJti) {
    }

    private record MintedSuccessor(long mintedAtMs, String jwt) {
    }

    private static final class Family {

        private final Map<String, Entry> entries = new HashMap<>();
        private final Map<String, MintedSuccessor> successors = new HashMap<>();
        private boolean revoked;
    }

    private final TestJwt jwt;
    private final JwtTokenService tokens;
    private final RecordingTokenBlacklist blacklist;
    private final boolean legacyAdmitEnabled;
    private final long retryReuseWindowMs;
    private final Map<String, Family> families = new HashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private final ScriptedFailures<Operation> failures =
            new ScriptedFailures<>(RecordingRefreshRotation.class.getSimpleName(), Operation.class);

    public RecordingRefreshRotation(TestJwt jwt, RecordingTokenBlacklist blacklist) {
        this(jwt, blacklist, DEFAULT_LEGACY_ADMIT_ENABLED, DEFAULT_RETRY_REUSE_WINDOW_MS);
    }

    public RecordingRefreshRotation(TestJwt jwt, RecordingTokenBlacklist blacklist,
            boolean legacyAdmitEnabled, long retryReuseWindowMs) {
        super(null, jwt.service(), jwt.service(), jwt.properties(), null, legacyAdmitEnabled, retryReuseWindowMs);
        this.jwt = jwt;
        this.tokens = jwt.service();
        this.blacklist = blacklist;
        this.legacyAdmitEnabled = legacyAdmitEnabled;
        this.retryReuseWindowMs = retryReuseWindowMs;
    }

    public void failNext(Operation operation, RuntimeException failure) {
        failures.failNext(operation, failure);
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public List<Rotation> rotations() {
        return calls.stream().filter(Rotation.class::isInstance).map(Rotation.class::cast).toList();
    }

    @Override
    public RotationResult rotate(String refreshToken) {
        RotationResult result = transition(refreshToken);
        calls.add(new Rotation(refreshToken, result));
        return result;
    }

    @Override
    public void revokeFamily(String familyId) {
        if (familyId == null) {
            return;
        }
        failIfScripted(Operation.REVOKE_FAMILY);
        family(familyId).revoked = true;
        calls.add(new FamilyRevoked(familyId));
    }

    private RotationResult transition(String refreshToken) {
        TokenClaims claims;
        try {
            claims = tokens.validateRefreshToken(refreshToken);
        } catch (InvalidTokenException e) {
            return invalid();
        }
        if (!claims.isRefreshToken()) {
            return invalid();
        }
        if (claims.jti() == null || claims.familyId() == null) {
            if (!legacyAdmitEnabled) {
                return invalid();
            }
            claims = admitLegacy(claims);
        }

        String successorJwt = tokens.generateRefreshToken(claims.userId(), claims.familyId(), claims.jti());
        TokenClaims successorClaims = tokens.validateRefreshToken(successorJwt);
        long nowMs = jwt.clock().millis();
        failIfScripted(Operation.ROTATE);

        if (blacklist.isUserTokenInvalidated(claims.userId(), claims.issuedAt().getTime())) {
            return new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY);
        }
        Family family = family(claims.familyId());
        if (family.revoked || revokedAtLogout(claims.familyId())) {
            return new RotationResult.Rejected(RotationResult.Rejected.Reason.REVOKED_FAMILY);
        }

        Entry current = family.entries.get(claims.jti());
        RotationState state = current == null ? RotationState.UNUSED_LATEST : current.state();
        if (state == RotationState.RETIRED || state == RotationState.SUPERSEDED) {
            family.revoked = true;
            return new RotationResult.Revoked(claims.familyId());
        }

        if (claims.parentJti() != null) {
            Entry parent = family.entries.get(claims.parentJti());
            if (parent != null && parent.state() == RotationState.PENDING) {
                family.entries.put(claims.parentJti(), new Entry(RotationState.RETIRED, null));
                family.successors.remove(claims.parentJti());
            }
        }

        if (state == RotationState.PENDING) {
            String previousSuccessor = current.successorJti();
            MintedSuccessor stored = family.successors.get(claims.jti());
            Entry previous = family.entries.get(previousSuccessor);
            if (stored != null && previous != null && previous.state() == RotationState.UNUSED_LATEST
                    && nowMs - stored.mintedAtMs() < retryReuseWindowMs) {
                return new RotationResult.Rotated(stored.jwt(), tokens.validateRefreshToken(stored.jwt()));
            }
            family.entries.put(previousSuccessor, new Entry(RotationState.SUPERSEDED, null));
        }

        family.entries.put(successorClaims.jti(), new Entry(RotationState.UNUSED_LATEST, null));
        family.entries.put(claims.jti(), new Entry(RotationState.PENDING, successorClaims.jti()));
        family.successors.put(claims.jti(), new MintedSuccessor(nowMs, successorJwt));
        return new RotationResult.Rotated(successorJwt, successorClaims);
    }

    private boolean revokedAtLogout(String familyId) {
        return blacklist.revocations().contains(new LogoutRevocation.FamilyRevocation(familyId));
    }

    private Family family(String familyId) {
        return families.computeIfAbsent(familyId, id -> new Family());
    }

    private static RotationResult invalid() {
        return new RotationResult.Rejected(RotationResult.Rejected.Reason.INVALID);
    }

    private static TokenClaims admitLegacy(TokenClaims legacy) {
        long issuedAtMs = legacy.issuedAt().getTime();
        String jti = UUID.nameUUIDFromBytes(String.format("legacy-jti:%d:%d", legacy.userId(), issuedAtMs)
                .getBytes(StandardCharsets.UTF_8)).toString();
        return new TokenClaims(legacy.userId(), legacy.type(), legacy.role(), legacy.issuedAt(),
                legacy.expiration(), jti, legacyFamilyId(legacy.userId(), issuedAtMs), legacy.parentJti());
    }

    private void failIfScripted(Operation operation) {
        failures.throwIfScripted(operation, failure -> calls.add(new Failed(operation, failure)));
    }
}
