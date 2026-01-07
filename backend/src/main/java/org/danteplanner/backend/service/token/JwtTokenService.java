package org.danteplanner.backend.service.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.danteplanner.backend.config.JwtProperties;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.InvalidTokenException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT token service implementing both generation and validation.
 * Uses JwtProperties for validated configuration values.
 */
@Service
public class JwtTokenService implements TokenGenerator, TokenValidator {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    // ==================== TokenGenerator Implementation ====================

    @Override
    public String generateAccessToken(Long userId, String email, UserRole role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_EMAIL, email);
        claims.put(CLAIM_TYPE, TokenClaims.TYPE_ACCESS);
        claims.put(CLAIM_ROLE, role.getValue());

        return buildToken(claims, email, jwtProperties.getAccessTokenExpiry());
    }

    @Override
    public String generateRefreshToken(Long userId, String email) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_TYPE, TokenClaims.TYPE_REFRESH);

        return buildToken(claims, email, jwtProperties.getRefreshTokenExpiry());
    }

    // ==================== TokenValidator Implementation ====================

    @Override
    public TokenClaims validateToken(String token) {
        Claims claims = parseToken(token);

        Long userId = claims.get(CLAIM_USER_ID, Long.class);
        String email = claims.getSubject();
        String type = claims.get(CLAIM_TYPE, String.class);
        String roleValue = claims.get(CLAIM_ROLE, String.class);

        // Parse role - null for old tokens or refresh tokens (backward compat)
        UserRole role = null;
        if (roleValue != null && UserRole.isValid(roleValue)) {
            role = UserRole.fromValue(roleValue);
        }

        if (userId == null) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MISSING_CLAIMS);
        }

        return new TokenClaims(
                userId,
                email,
                type,
                role,
                claims.getIssuedAt(),
                claims.getExpiration()
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
     * Extracts email from a token.
     *
     * @param token JWT token string
     * @return email from token subject
     * @throws InvalidTokenException if token is invalid
     */
    public String getEmailFromToken(String token) {
        TokenClaims claims = validateToken(token);
        return claims.email();
    }

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
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
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
        }
    }
}
