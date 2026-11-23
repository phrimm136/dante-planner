package org.danteplanner.backend.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tokens are RS256-signed with cleartext claims and {@code sub}=userId.
 */
@Service
@Slf4j
public class JwtTokenService implements TokenGenerator, TokenValidator {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_JTI = "jti";
    private static final String CLAIM_FAMILY_ID = "family_id";
    private static final String CLAIM_PARENT_JTI = "parent_jti";

    private final JwtProperties jwtProperties;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final JwtParser accessParser;
    private final JwtParser refreshParser;
    private final Clock clock;

    public JwtTokenService(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.privateKey = jwtProperties.getPrivateKey();
        this.publicKey = jwtProperties.getPublicKey();
        this.accessParser = Jwts.parser()
                .verifyWith(publicKey)
                .clock(() -> Date.from(clock.instant()))
                .require(CLAIM_TYPE, TYPE_ACCESS)
                .build();
        this.refreshParser = Jwts.parser()
                .verifyWith(publicKey)
                .clock(() -> Date.from(clock.instant()))
                .require(CLAIM_TYPE, TYPE_REFRESH)
                .build();
    }

    @Override
    public String generateAccessToken(Long userId, UserRole role) {
        Assert.notNull(userId, "userId must not be null");
        Assert.notNull(role, "role must not be null");

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_ACCESS);
        claims.put(CLAIM_ROLE, role.getValue());

        return buildToken(claims, userId.toString(), jwtProperties.getAccessTokenExpiry());
    }

    @Override
    public String generateRefreshToken(Long userId) {
        return buildRefreshToken(userId, UUID.randomUUID().toString(), Map.of());
    }

    @Override
    public String generateRefreshToken(Long userId, String familyId, String parentJti) {
        return buildRefreshToken(userId, familyId, parentJti != null ? Map.of(CLAIM_PARENT_JTI, parentJti) : Map.of());
    }

    private String buildRefreshToken(Long userId, String familyId, Map<String, Object> parentClaim) {
        Assert.notNull(userId, "userId must not be null");
        Assert.notNull(familyId, "familyId must not be null");

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_REFRESH);
        claims.put(CLAIM_JTI, UUID.randomUUID().toString());
        claims.put(CLAIM_FAMILY_ID, familyId);
        claims.putAll(parentClaim);

        return buildToken(claims, userId.toString(), jwtProperties.getRefreshTokenExpiry());
    }

    @Override
    public AccessClaims validateAccessToken(String token) {
        return parse(accessParser, token, JwtTokenService::toAccessClaims);
    }

    @Override
    public RefreshClaims validateRefreshToken(String token) {
        return parse(refreshParser, token, JwtTokenService::toRefreshClaims);
    }

    private static AccessClaims toAccessClaims(Claims claims) {
        return new AccessClaims(
                userIdOf(claims), roleOf(claims), required(claims.getIssuedAt()), expirationOf(claims));
    }

    private static RefreshClaims toRefreshClaims(Claims claims) {
        return new RefreshClaims(
                userIdOf(claims),
                required(claims.getIssuedAt()),
                expirationOf(claims),
                RefreshLineage.of(
                        required(claims.get(CLAIM_JTI, String.class)),
                        required(claims.get(CLAIM_FAMILY_ID, String.class)),
                        claims.get(CLAIM_PARENT_JTI, String.class)));
    }

    private static Long userIdOf(Claims claims) {
        try {
            return Long.valueOf(required(claims.getSubject()));
        } catch (NumberFormatException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        }
    }

    private static UserRole roleOf(Claims claims) {
        String roleValue = claims.get(CLAIM_ROLE, String.class);
        if (roleValue != null && UserRole.isValid(roleValue)) {
            return UserRole.fromValue(roleValue);
        }
        return UserRole.NORMAL;
    }

    private static Date expirationOf(Claims claims) {
        Date expiration = required(claims.getExpiration());
        if (expiration.before(required(claims.getIssuedAt()))) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED);
        }
        return expiration;
    }

    private static <T> T required(@Nullable T claim) {
        if (claim == null) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MISSING_CLAIMS);
        }
        return claim;
    }

    private String buildToken(Map<String, Object> claims, String subject, Long expiryMs) {
        Date now = Date.from(clock.instant());
        Date expiration = new Date(now.getTime() + expiryMs);

        try {
            return Jwts.builder()
                    .claims(claims)
                    .subject(subject)
                    .issuedAt(now)
                    .expiration(expiration)
                    .signWith(privateKey, SignatureAlgorithm.RS256)
                    .compact();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Token generation failed: {}", e.getMessage(), e);
            throw new TokenGenerationException("Failed to generate JWT token", e);
        }
    }

    private static <T> T parse(JwtParser parser, String token, Function<Claims, T> toClaims) {
        try {
            return toClaims.apply(parser.parseSignedClaims(token).getPayload());
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.EXPIRED, e);
        } catch (MalformedJwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        } catch (SignatureException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID_SIGNATURE, e);
        } catch (IncorrectClaimException | MissingClaimException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID_TYPE, e);
        } catch (JwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        } catch (IllegalArgumentException e) {
            // A null, empty or whitespace-only token never reaches the JJWT parser's own errors.
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        }
    }
}
