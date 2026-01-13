# HTTP Response Codes and Frontend Handling

> **Context**: Lessons learned from debugging authentication session bugs in LimbusPlanner (2026-01-13)

## Executive Summary

Three authentication bugs stemmed from incorrect assumptions about HTTP response handling:

| Bug | Assumption | Reality | Impact |
|-----|------------|---------|--------|
| Logout fails | All 2xx have JSON body | 204 has no body | SyntaxError on `.json()` |
| Errors masked | All errors = "not logged in" | 401 ≠ 500 ≠ network | Wrong UI state |
| ~~Session reset~~ | ~~All `/auth/*` shouldn't retry~~ | ~~`/auth/me` SHOULD retry~~ | ~~Premature logout~~ |

> **Correction (2026-01-13)**: Bug 3 analysis was incorrect. `/auth/me` returning 401 for unauthenticated users is expected behavior—it should NOT trigger token refresh. The original `!endpoint.includes('/auth/')` was intentional. The actual session reset issue is likely backend token lifetime configuration.

---

## HTTP Status Code Categories

HTTP status codes are grouped by first digit:
- **1xx**: Informational (rarely seen in REST)
- **2xx**: Success (but success means different things)
- **3xx**: Redirection
- **4xx**: Client error (your fault)
- **5xx**: Server error (their fault)

### 2xx Success Codes

| Code | Name | Body? | Use Case | Frontend Handling |
|------|------|-------|----------|-------------------|
| 200 | OK | Yes | Standard success | Parse JSON |
| 201 | Created | Yes | Resource created (POST) | Parse JSON |
| 202 | Accepted | Optional | Async operation started | Check body first |
| **204** | No Content | **No** | Success, nothing to return | **Skip JSON parse** |

**Key Learning**: 204 means "success, nothing to send back." Calling `.json()` throws `SyntaxError: Unexpected end of JSON input`.

### 4xx Client Errors

| Code | Name | Meaning | Frontend Action |
|------|------|---------|-----------------|
| 400 | Bad Request | Invalid input | Show validation error |
| 401 | Unauthorized | No/invalid credentials | Login OR refresh token |
| 403 | Forbidden | Valid creds, no permission | Show "access denied" |
| 404 | Not Found | Resource doesn't exist | Show "not found" |
| 409 | Conflict | State conflict | Prompt user to resolve |
| 429 | Too Many Requests | Rate limited | Show retry message |

**Key Learning**: 401 has two contexts:
1. **Guest user** (no token) → Show login UI
2. **Expired token** (had token, now stale) → Trigger refresh flow

### 5xx Server Errors

| Code | Name | Meaning | Frontend Action |
|------|------|---------|-----------------|
| 500 | Internal Server Error | Server bug | Show error, log to Sentry |
| 502 | Bad Gateway | Upstream failed | Show "try again" |
| 503 | Service Unavailable | Overloaded/maintenance | Show status page |
| 504 | Gateway Timeout | Upstream timeout | Retry with backoff |

**Key Learning**: 5xx should NEVER be masked as user state. Showing "Sign In" when server is down causes user confusion.

---

## Response Handling Patterns

### Pattern 1: Safe JSON Parsing

```
WRONG:
fetch() → response.json()  // Throws on 204

RIGHT:
fetch() → check status → 204? return undefined : response.json()
```

**Rule**: Check if response has body before parsing.

### Pattern 2: Error Type Distinction

```
WRONG:
catch (error) → return null  // All errors = guest

RIGHT:
catch (error) →
  401 → return null (guest)
  5xx → toast.error() (server issue)
  network → toast.error() (connectivity)
```

**Rule**: Different errors need different feedback.

### Pattern 3: Retry Exclusion by Semantics

```
ACTUALLY CORRECT:
!endpoint.includes('/auth/')  // /auth/* = authentication endpoints

WHY: /auth/me returning 401 means "not authenticated" - expected for guests.
     Triggering refresh on /auth/me creates infinite loops when user has no session.
```

**Rule**: Understand endpoint semantics, not just technical pattern matching.

| Endpoint | 401 Meaning | Refresh? |
|----------|-------------|----------|
| `/api/planner/123` | Token expired | Yes |
| `/api/auth/me` | Not logged in (guest) | No |
| `/api/auth/refresh` | Refresh token invalid | No (prevent loop) |

---

## REST API Response Conventions

| Operation | Success Code | Body |
|-----------|--------------|------|
| GET (single) | 200 | Entity |
| GET (list) | 200 | Array/Page |
| POST (create) | 201 | Created entity |
| PUT (replace) | 200 | Updated entity |
| PATCH (update) | 200 | Updated entity |
| DELETE | **204** | **None** |
| Action (no return) | **204** | **None** |

Logout is an "action with no return value" → 204 is correct. The bug was client-side.

---

## Token Refresh Flow (OAuth 2.0)

```
Request with expired access token
    → Server returns 401
    → Client detects 401 (NOT on auth endpoints)
    → Client calls /refresh with refresh token
    → Server returns new access token
    → Client retries original request
```

**Key Learning**: Refresh flow should only trigger on protected resource endpoints (e.g., `/api/planner/*`), not on authentication endpoints (e.g., `/api/auth/*`).

### Why /auth/me Should NOT Trigger Refresh

```
User visits site (no session)
    → /auth/me returns 401 (expected: "you're not logged in")

If refresh triggers on /auth/me:
    → /auth/me 401 → refresh (no token) → 401 → redirect to "/"
    → Page reloads → /auth/me 401 → infinite loop
```

---

## Code Quality Lessons

### 1. Understand Semantics Before "Fixing" Patterns

```typescript
// Original (thought it was wrong)
!endpoint.includes('/auth/')

// "Fix" that caused infinite loops
!endpoint.endsWith('/auth/refresh') && !endpoint.endsWith('/auth/logout')

// Lesson: The original was correct - /auth/* endpoints handle their own auth state
```

**Rule**: Before changing "broad" patterns, understand WHY they exist.

### 2. Error Swallowing Masks Problems

```typescript
// Dangerous
try { ... } catch { return null; }

// Safe
try { ... } catch (error) {
  if (isAuthError(error)) return null;
  throw error; // Let boundary handle
}
```

### 3. API Clients Need Response-Type Awareness

```typescript
// Dangerous
return response.json();

// Safe
if (response.status === 204) return undefined;
return response.json();
```

### 4. Default Values in Error Extraction Are Dangerous

```typescript
// DANGEROUS: status = 0 catches too much
const statusMatch = error.message.match(/status:\s*(\d+)/);
const status = statusMatch ? parseInt(statusMatch[1], 10) : 0;  // ← 0 is a trap

if (status >= 500 || status === 0) {  // Network errors, redirects, unknown → all toast
  toast.error('...');  // Infinite toast loop on redirect errors
}

// SAFE: Use null for "unknown"
const status = statusMatch ? parseInt(statusMatch[1], 10) : null;

if (status !== null && status >= 500) {  // Only explicit 5xx
  toast.error('...');
}
```

**Rule**: When extracting values from error messages, use `null` not `0` for "not found" - they have different meanings.

---

## Summary

| Concept | Wrong | Correct |
|---------|-------|---------|
| 204 response | Has empty JSON `{}` | Has **no body** |
| 401 on `/auth/me` | Should trigger refresh | Expected for guests, return null |
| 401 on `/api/planner/*` | User not logged in | Token expired, trigger refresh |
| 5xx error | Same as 401 | Server problem, show error |
| Path matching for auth | Use exact endpoint list | Group by semantic domain (`/auth/*`) |
| Error extraction default | Use 0 for "not found" | Use null (0 catches too much) |

---

## Test Coverage

Tests written to validate these patterns:

| File | Tests | Coverage |
|------|-------|----------|
| `src/lib/api.test.ts` | 14 | 204 handling, 401 refresh flow, 409 ConflictError |
| `src/hooks/useAuthQuery.test.tsx` | 14 | Error distinction logic, login/logout mutations |

### Key Test Patterns

```typescript
// Mock sequential responses for refresh flow
mockFetch
  .mockResolvedValueOnce({ ok: false, status: 401 })  // Original fails
  .mockResolvedValueOnce({ ok: true, status: 200 })   // Refresh succeeds
  .mockResolvedValueOnce({ ok: true, status: 200, json: ... })  // Retry succeeds

// Test error status extraction
const testCases = [
  { message: 'HTTP error! status: 401', expected: 401 },
  { message: 'HTTP error! status: 500', expected: 500 },
  { message: 'Network error', expected: null },  // No status → null, not 0
];
```

---

## Meta-Lesson

HTTP is a protocol with precise semantics. Status codes aren't just "success" or "failure" - each communicates specific meaning.

When building API clients, ask:
1. What response codes can this endpoint return?
2. Which have bodies, which don't?
3. Which errors are recoverable vs fatal?
4. Which need user feedback vs silent handling?

### The Bigger Lesson: Understand Before "Fixing"

This debugging session had a critical mistake: **changing code before understanding WHY it was written that way**.

The `/auth/` exclusion pattern looked "wrong" because it seemed too broad. The "fix" to use exact endpoint matching caused infinite refresh loops. The original pattern was correct—it was grouping endpoints by *semantic domain* (authentication endpoints handle their own auth state).

**Before changing any "suspicious" code:**
1. Write a test that captures current behavior
2. Ask: What scenario does this handle?
3. Remove the code mentally—what breaks?
4. Only then: decide if change is needed
