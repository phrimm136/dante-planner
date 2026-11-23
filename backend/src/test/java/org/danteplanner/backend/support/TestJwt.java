package org.danteplanner.backend.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.jsonwebtoken.Jwts;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.RefreshClaims;
import org.danteplanner.backend.auth.token.RefreshLineage;
import org.danteplanner.backend.shared.config.JwtProperties;

public final class TestJwt {

    private static final KeyPair KEY_PAIR = generateKeyPair();

    private final SteppedClock clock;
    private final JwtProperties properties;
    private final JwtTokenService service;

    public TestJwt() {
        this(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public TestJwt(Instant start) {
        this.clock = new SteppedClock(start);
        this.properties = new JwtProperties(
                KEY_PAIR.getPrivate(), KEY_PAIR.getPublic(), new byte[0], 900000L, 604800000L);
        this.service = new JwtTokenService(properties, clock);
    }

    public JwtTokenService service() {
        return service;
    }

    public SteppedClock clock() {
        return clock;
    }

    public JwtProperties properties() {
        return properties;
    }

    public Map<String, Object> accessTokenPayload(Long userId) {
        Map<String, Object> payload = payload(userId, "access", properties.getAccessTokenExpiry());
        payload.put("role", "NORMAL");
        return payload;
    }

    public Map<String, Object> refreshTokenPayload(Long userId) {
        Map<String, Object> payload = payload(userId, "refresh", properties.getRefreshTokenExpiry());
        payload.put("jti", "jti-1");
        payload.put("family_id", "fam-1");
        return payload;
    }

    private Map<String, Object> payload(Long userId, String type, long expiryMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", userId.toString());
        payload.put("type", type);
        payload.put("iat", Date.from(clock.instant()));
        payload.put("exp", Date.from(clock.instant().plusMillis(expiryMs)));
        return payload;
    }

    public String sign(Map<String, Object> payload) {
        return Jwts.builder().claims(payload).signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256).compact();
    }

    public String refreshTokenWithoutFamily(Long userId) {
        Map<String, Object> payload = refreshTokenPayload(userId);
        payload.remove("family_id");
        return sign(payload);
    }

    public static String jtiOf(RefreshClaims claims) {
        return claims.lineage().jti();
    }

    public static String familyIdOf(RefreshClaims claims) {
        return claims.lineage().familyId();
    }

    public static String parentJtiOf(RefreshClaims claims) {
        if (claims.lineage() instanceof RefreshLineage.Child child) {
            return child.parentJti();
        }
        throw new AssertionError("only a rotated child carries a parent: " + claims.lineage());
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class SteppedClock extends Clock {

        private Instant now;

        private SteppedClock(Instant start) {
            this.now = start;
        }

        public void advance(Duration step) {
            now = now.plus(step);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
