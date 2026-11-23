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

import org.springframework.beans.BeanUtils;

import io.jsonwebtoken.Jwts;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.RefreshLineage;
import org.danteplanner.backend.auth.token.TokenClaims;
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
        try {
            this.service = BeanUtils.instantiateClass(
                    JwtTokenService.class.getDeclaredConstructor(JwtProperties.class, Clock.class), properties, clock);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
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

    public String legacyRefreshToken(Long userId) {
        Date issuedAt = Date.from(clock.instant());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", TokenClaims.TYPE_REFRESH)
                .issuedAt(issuedAt)
                .expiration(new Date(issuedAt.getTime() + properties.getRefreshTokenExpiry()))
                .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public static String jtiOf(TokenClaims claims) {
        return switch (claims.lineage()) {
            case RefreshLineage.Root root -> root.jti();
            case RefreshLineage.Child child -> child.jti();
            case RefreshLineage.Legacy legacy -> throw new AssertionError("a legacy token carries no jti: " + legacy);
        };
    }

    public static String familyIdOf(TokenClaims claims) {
        return switch (claims.lineage()) {
            case RefreshLineage.Root root -> root.familyId();
            case RefreshLineage.Child child -> child.familyId();
            case RefreshLineage.Legacy legacy -> throw new AssertionError("a legacy token carries no family: " + legacy);
        };
    }

    public static String parentJtiOf(TokenClaims claims) {
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
