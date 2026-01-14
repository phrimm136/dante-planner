# Auth Error Code Design: Semantic 401 Responses

> **Context**: Refactoring authentication error codes for proper token refresh handling in LimbusPlanner (2026-01-14)

## Executive Summary

The original auth system returned generic `UNAUTHORIZED` and `INVALID_TOKEN` error codes, causing incorrect frontend behavior. The refactored system uses **semantic error codes** that clearly indicate the appropriate client action.

| Old Code | New Code | Meaning | Client Action |
|----------|----------|---------|---------------|
| `INVALID_TOKEN` | `TOKEN_EXPIRED` | Token lifetime exceeded | Refresh |
| `INVALID_TOKEN` | `TOKEN_INVALID` | Token malformed/tampered | Logout |
| `TOKEN_REVOKED` | `TOKEN_REVOKED` | Explicitly revoked | Logout |
| `UNAUTHORIZED` | `TOKEN_MISSING` | No access token | Guest state |
| `UNAUTHORIZED` | `REFRESH_TOKEN_MISSING` | No refresh token | Logout |
| `ACCOUNT_DELETED` | `ACCOUNT_DELETED` | Account soft-deleted | Logout |

---

## The Problem

### Original Behavior

```
401 INVALID_TOKEN → Frontend attempts refresh
401 UNAUTHORIZED  → Frontend treats as guest
```

**Issue 1**: `INVALID_TOKEN` covered both expired tokens (recoverable) and malformed tokens (unrecoverable). Frontend unnecessarily attempted refresh for tampered tokens.

**Issue 2**: `UNAUTHORIZED` covered both "no token" (guest) and "refresh token missing" (session corruption). Frontend couldn't distinguish these cases.

### The Debugging Session

Initial symptom: Auth refresh didn't work during page refresh.

Investigation revealed:
1. Cookies weren't being sent due to cross-origin issues (separate problem)
2. When cookies DID arrive but were expired, the error codes were ambiguous
3. Frontend logic `errorCode === 'INVALID_TOKEN'` triggered refresh for all invalid tokens, including malformed ones

---

## The Solution

### 1. Typed Exception with Reason Enum

Backend already had `InvalidTokenException` with a `Reason` enum:

```java
public enum Reason {
    EXPIRED("Token has expired"),
    MALFORMED("Token is malformed"),
    INVALID_SIGNATURE("Token signature is invalid"),
    MISSING_CLAIMS("Token is missing required claims"),
    INVALID_TYPE("Token type is invalid for this operation"),
    REVOKED("Token has been revoked");
}
```

The filter now maps these reasons to specific error codes:

```java
private String mapReasonToErrorCode(InvalidTokenException.Reason reason) {
    return switch (reason) {
        case EXPIRED -> "TOKEN_EXPIRED";
        case MALFORMED -> "TOKEN_INVALID";
        case INVALID_SIGNATURE -> "TOKEN_INVALID";
        case MISSING_CLAIMS -> "TOKEN_INVALID";
        case INVALID_TYPE -> "TOKEN_INVALID";
        case REVOKED -> "TOKEN_REVOKED";
    };
}
```

### 2. Controller Error Codes

AuthController now returns distinct codes for missing tokens:

```java
// /api/auth/me - no access token
Map.of("error", "TOKEN_MISSING", "message", "No access token provided")

// /api/auth/refresh - no refresh token
Map.of("error", "REFRESH_TOKEN_MISSING", "message", "No refresh token provided")
```

### 3. Frontend Refresh Logic

Frontend only refreshes on `TOKEN_EXPIRED`:

```typescript
const shouldRefresh =
  errorCode === 'TOKEN_EXPIRED' &&
  !endpoint.endsWith('/auth/refresh') &&
  !endpoint.endsWith('/auth/logout');
```

---

## Design Principles

### Principle 1: Error Codes Should Indicate Action

Don't: Return what went wrong (technical)
Do: Return what the client should do (semantic)

| Code | Technical Meaning | Semantic Meaning |
|------|-------------------|------------------|
| `TOKEN_EXPIRED` | JWT exp claim in past | Try refresh |
| `TOKEN_INVALID` | JWT parse/verify failed | Session corrupted, logout |
| `TOKEN_MISSING` | No cookie present | User not logged in |

### Principle 2: Refresh Should Be Narrowly Scoped

Only one condition should trigger refresh: **expired but otherwise valid token**.

All other 401 scenarios indicate either:
- Guest state (no token)
- Corrupted session (invalid token)
- Explicit revocation (logout elsewhere)

Refreshing in these cases wastes resources and can cause loops.

### Principle 3: Avoid Ambiguous Fallback Codes

`UNAUTHORIZED` was doing too much work:
- Guest user viewing public page
- Logged-in user whose cookie wasn't transmitted
- User whose refresh token expired
- User attempting refresh without refresh token

Each needs different handling. Specific codes enable specific handling.

---

## Refresh Token Rotation

The system already implements refresh token rotation (each refresh invalidates the old token):

```java
// AuthenticationFacade.refreshTokens()
tokenBlacklistService.blacklistToken(refreshToken, claims.expiration());
String newRefreshToken = tokenGenerator.generateRefreshToken(user.getId(), user.getEmail());
```

This prevents refresh token replay attacks.

---

## Error Code Summary

### Backend Returns

| Endpoint | Scenario | Error Code |
|----------|----------|------------|
| Any | Token expired | `TOKEN_EXPIRED` |
| Any | Token malformed/tampered | `TOKEN_INVALID` |
| Any | Token revoked | `TOKEN_REVOKED` |
| Any | Account deleted | `ACCOUNT_DELETED` |
| `/auth/me` | No access token | `TOKEN_MISSING` |
| `/auth/refresh` | No refresh token | `REFRESH_TOKEN_MISSING` |

### Frontend Handles

| Error Code | Action |
|------------|--------|
| `TOKEN_EXPIRED` | Call `/auth/refresh`, retry original request |
| `TOKEN_INVALID` | Redirect to home (session corrupted) |
| `TOKEN_REVOKED` | Redirect to home (logged out elsewhere) |
| `TOKEN_MISSING` | Return null (guest state) |
| `REFRESH_TOKEN_MISSING` | Redirect to home (session lost) |
| `ACCOUNT_DELETED` | Redirect to home |

---

## Testing Strategy

### Backend Tests

```java
@Test
void doFilterInternal_expiredToken_returnsTokenExpired() {
    when(tokenValidator.validateToken(token))
        .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

    filter.doFilterInternal(request, response, filterChain);

    assertTrue(responseBody.contains("TOKEN_EXPIRED"));
}
```

### Frontend Tests

```typescript
it('triggers refresh for TOKEN_EXPIRED', async () => {
  mockFetch
    .mockResolvedValueOnce({ status: 401, json: () => ({ error: 'TOKEN_EXPIRED' }) })
    .mockResolvedValueOnce({ status: 200 })  // refresh
    .mockResolvedValueOnce({ status: 200, json: () => ({ id: 1 }) });  // retry

  await ApiClient.get('/api/planner/123');
  expect(mockFetch).toHaveBeenCalledTimes(3);  // original, refresh, retry
});

it('does NOT refresh for TOKEN_MISSING', async () => {
  mockFetch.mockResolvedValue({ status: 401, json: () => ({ error: 'TOKEN_MISSING' }) });

  await expect(ApiClient.get('/api/auth/me')).rejects.toThrow();
  expect(mockFetch).toHaveBeenCalledTimes(1);  // no refresh attempt
});
```

---

## Key Lessons

### 1. Exception Design Patterns

The `InvalidTokenException` with `Reason` enum is a good pattern:
- Single exception type (no type explosion)
- Typed reasons enable specific handling
- Message generation from reason (DRY)
- Easy to add new reasons without changing signatures

### 2. Error Codes Are API Contracts

Error codes are part of your API contract. Changes require:
- Frontend coordination
- Test updates
- Documentation updates

Semantic codes (`TOKEN_EXPIRED`) are more stable than implementation codes (`JWT_EXP_CLAIM_EXCEEDED`).

### 3. Refresh Logic Is Security-Sensitive

Overly aggressive refresh logic can:
- Cause infinite loops (refreshing on refresh failures)
- Waste resources (refreshing when refresh can't help)
- Mask bugs (hiding the real error behind retry attempts)

Be conservative: only refresh when you have high confidence it will help.

---

## Related Files

| File | Purpose |
|------|---------|
| `JwtAuthenticationFilter.java` | Maps exception reasons to error codes |
| `AuthController.java` | Returns TOKEN_MISSING, REFRESH_TOKEN_MISSING |
| `InvalidTokenException.java` | Defines Reason enum |
| `api.ts` | Frontend refresh logic |
| `api.test.ts` | Frontend error handling tests |
| `JwtAuthenticationFilterTest.java` | Backend error code tests |
