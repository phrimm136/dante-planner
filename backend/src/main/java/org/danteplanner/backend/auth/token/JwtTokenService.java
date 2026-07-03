package org.danteplanner.backend.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT token service implementing both generation and validation.
 * Tokens are RS256-signed with cleartext claims and {@code sub}=userId.
 */
@Service
@Slf4j
public class JwtTokenService implements TokenGenerator, TokenValidator {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_JTI = "jti";
    private static final String CLAIM_FAMILY_ID = "family_id";
    private static final String CLAIM_PARENT_JTI = "parent_jti";

    private final JwtProperties jwtProperties;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final JwtParser jwtParser;
    private final Clock clock;

    @Autowired
    public JwtTokenService(JwtProperties jwtProperties) {
        this(jwtProperties, Clock.systemUTC());
    }

    JwtTokenService(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.privateKey = jwtProperties.getPrivateKey();
        this.publicKey = jwtProperties.getPublicKey();
        this.jwtParser = Jwts.parser()
                .verifyWith(publicKey)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    // ==================== TokenGenerator Implementation ====================

    @Override
    public String generateAccessToken(Long userId, UserRole role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TokenClaims.TYPE_ACCESS);
        claims.put(CLAIM_ROLE, role.getValue());

        return buildToken(claims, userId.toString(), jwtProperties.getAccessTokenExpiry());
    }

    @Override
    public String generateRefreshToken(Long userId) {
        return generateRefreshToken(userId, UUID.randomUUID().toString(), null);
    }

    @Override
    public String generateRefreshToken(Long userId, String familyId, String parentJti) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (familyId == null) {
            throw new IllegalArgumentException("familyId must not be null");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TokenClaims.TYPE_REFRESH);
        claims.put(CLAIM_JTI, UUID.randomUUID().toString());
        claims.put(CLAIM_FAMILY_ID, familyId);
        if (parentJti != null) {
            claims.put(CLAIM_PARENT_JTI, parentJti);
        }

        return buildToken(claims, userId.toString(), jwtProperties.getRefreshTokenExpiry());
    }

    // ==================== TokenValidator Implementation ====================

    @Override
    public TokenClaims validateToken(String token) {
        Claims claims = parseToken(token);

        String subject = claims.getSubject();
        if (subject == null) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MISSING_CLAIMS);
        }
        Long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        }

        String type = claims.get(CLAIM_TYPE, String.class);
        String roleValue = claims.get(CLAIM_ROLE, String.class);
        String jti = claims.get(CLAIM_JTI, String.class);
        String familyId = claims.get(CLAIM_FAMILY_ID, String.class);
        String parentJti = claims.get(CLAIM_PARENT_JTI, String.class);

        // Parse role - null for old tokens or refresh tokens (backward compat)
        UserRole role = null;
        if (roleValue != null && UserRole.isValid(roleValue)) {
            role = UserRole.fromValue(roleValue);
        }

        return new TokenClaims(
                userId,
                null,
                type,
                role,
                claims.getIssuedAt(),
                claims.getExpiration(),
                jti,
                familyId,
                parentJti
        );
    }

    @Override
    public Long getUserIdFromToken(String token) {
        TokenClaims claims = validateToken(token);
        return claims.userId();
    }

    @Override
    public boolean isTokenExpired(String token) {
        try {
            TokenClaims claims = validateToken(token);
            return claims.isExpired();
        } catch (InvalidTokenException e) {
            // Invalid tokens are treated as expired
            return true;
        }
    }

    // ==================== Additional Methods ====================

    /**
     * Gets the token type (access or refresh).
     *
     * @param token JWT token string
     * @return token type
     * @throws InvalidTokenException if token is invalid
     */
    public String getTokenType(String token) {
        TokenClaims claims = validateToken(token);
        return claims.type();
    }

    // ==================== Private Helpers ====================

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
        } catch (Exception e) {
            log.error("Token generation failed: {}", e.getMessage(), e);
            throw new TokenGenerationException("Failed to generate JWT token", e);
        }
    }

    private Claims parseToken(String token) {
        try {
            return jwtParser
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.EXPIRED, e);
        } catch (MalformedJwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        } catch (SignatureException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID_SIGNATURE, e);
        } catch (JwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        } catch (Exception e) {
            log.error("Unexpected token parsing failure: {}", e.getMessage(), e);
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        }
    }
}
