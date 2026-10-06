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
        this.properties = new JwtProperties();
        properties.setPrivateKey(KEY_PAIR.getPrivate());
        properties.setPublicKey(KEY_PAIR.getPublic());
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
