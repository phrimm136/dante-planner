# Docker Rate Limiting Architecture

Session: Dockerization with Nginx Reverse Proxy
Date: 2026-01-12

## Problem Statement

When containerizing a Spring Boot application behind nginx reverse proxy, all HTTP requests appear to originate from the nginx container's IP (e.g., 172.18.0.2) due to Docker NAT. This breaks IP-based rate limiting because all users share a single rate limit bucket.

## Architecture Before

```
Client (203.0.113.1) → Backend (sees 203.0.113.1)
                       ↓
                       Rate limit bucket: "203.0.113.1:auth"
```

Each client gets their own bucket. Rate limiting works correctly.

## Architecture After (Naive Docker)

```
Client (203.0.113.1) → Nginx (172.18.0.2) → Backend (sees 172.18.0.2)
                                            ↓
                                            Rate limit bucket: "172.18.0.2:auth"
```

All clients share one bucket. Denial of service vulnerability.

## Architecture After (Fixed)

```
Client → Nginx → Backend
         │       ↓
         │       X-Forwarded-For: 203.0.113.1
         │       X-Device-ID: abc-123
         │       ↓
         │       1. Check if direct IP (172.18.0.2) is trusted proxy
         │       2. If yes, extract X-Forwarded-For (203.0.113.1)
         │       3. If IP is private, use device ID fallback
         │       ↓
         └──────→ Rate limit bucket: "ip:203.0.113.1:auth"
                  OR "device:abc-123:auth" (for private IPs)
```

## Key Components

### 1. SecurityProperties (CIDR Support)

Parses trusted proxy configuration supporting both exact IPs and CIDR notation.

- Input: `TRUSTED_PROXY_IPS=127.0.0.1,172.18.0.0/16`
- Output: `isTrustedProxy("172.18.0.5")` returns true

Implementation uses `CidrRange` inner class with byte-level subnet matching.

### 2. ClientIpResolver (Identifier Resolution)

Priority order for determining client identity:
1. CF-Connecting-IP (Cloudflare, highest trust)
2. X-Forwarded-For (if from trusted proxy)
3. RemoteAddr (direct connection fallback)

If resolved IP is private (RFC 1918), falls back to device ID.

### 3. RateLimitConfig (Bucket Management)

- Bucket key format: `{identifier}:{endpoint}` where identifier is `ip:xxx` or `device:xxx`
- TTL-based eviction prevents memory exhaustion (default: 1 hour)
- Max bucket limit (10000) enforced via scheduled cleanup

## RFC 1918 Private Ranges

| Range | CIDR | Use Case |
|-------|------|----------|
| 10.0.0.0 - 10.255.255.255 | 10.0.0.0/8 | Large private networks |
| 172.16.0.0 - 172.31.255.255 | 172.16.0.0/12 | Docker bridge default |
| 192.168.0.0 - 192.168.255.255 | 192.168.0.0/16 | Home networks |
| 127.0.0.0 - 127.255.255.255 | 127.0.0.0/8 | Loopback |

## Failure Modes

| Scenario | Symptom | Fix |
|----------|---------|-----|
| CIDR not parsed | All Docker requests blocked | Use isTrustedProxy() not Set.contains() |
| Private IP not detected | All users share bucket | Implement isPrivateIp() check |
| Device ID missing | Falls back to "unknown" | Client must send X-Device-ID header |
| Bucket map unbounded | Memory leak over time | Add TTL eviction + max limit |

## Session Timeline

1. **Initial Docker test**: Flyway migration failed with charset mismatch
2. **Fix V015**: Added ENGINE/CHARSET clause to planner_comments table
3. **Fix V023**: Changed DROP INDEX syntax for MySQL compatibility
4. **Actuator missing**: Added spring-boot-starter-actuator for health checks
5. **CORS blocked**: Fixed property name mismatch (security.cors → cors)
6. **Frontend refused**: Updated VITE_API_BASE_URL from :8080 to :80
7. **Security review**: Added CIDR support to SecurityProperties
8. **Performance review**: Added bucket eviction to RateLimitConfig

## Lessons Learned

1. **Test migrations in Docker early**: Charset issues only appear at container startup
2. **CIDR requires custom parsing**: Spring doesn't support subnet notation out of box
3. **Rate limiting failures are silent**: No error thrown, just shared quotas
4. **Health checks must be app-specific**: TCP socket check insufficient
5. **Environment variable precedence matters**: Document override order clearly
