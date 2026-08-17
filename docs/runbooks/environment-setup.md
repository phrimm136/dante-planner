# Environment Setup and Configuration Guide

Detailed guide for configuring LimbusPlanner across different environments.

## Table of Contents

- [Environment Variables](#environment-variables)
- [Docker Network Architecture](#docker-network-architecture)
- [Rate Limiting Configuration](#rate-limiting-configuration)
- [OAuth Configuration](#oauth-configuration)
- [CORS Configuration](#cors-configuration)
- [Database Configuration](#database-configuration)
- [Troubleshooting](#troubleshooting)

## Environment Variables

All configuration is done via environment variables in the `.env` file.

### Required Variables

| Variable | Description | Example | Notes |
|----------|-------------|---------|-------|
| `MYSQL_ROOT_PASSWORD` | MySQL root password | `$(openssl rand -base64 32)` | **Critical**: Never use default |
| `MYSQL_DATABASE` | Database name | `danteplanner` | Must match migration scripts |
| `MYSQL_USER` | Application database user | `danteplanner` | Non-root user for security |
| `MYSQL_PASSWORD` | Application user password | `$(openssl rand -base64 32)` | Different from root password |
| `JWT_SECRET` | JWT signing key | `$(openssl rand -base64 48)` | Min 32 bytes, rotate periodically |
| `GOOGLE_OAUTH_CLIENT_ID` | Google OAuth Client ID | `123456-abc.apps.googleusercontent.com` | From Google Cloud Console |
| `GOOGLE_OAUTH_CLIENT_SECRET` | Google OAuth Secret | `GOCSPX-...` | Keep confidential |
| `TRUSTED_PROXY_IPS` | Trusted proxy IP ranges | `172.18.0.0/16,103.21.244.0/22` | **Critical**: Must include Docker network |

### Optional Variables (with defaults)

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_HOST` | `mysql` | Database hostname (Docker service name) |
| `MYSQL_PORT` | `3306` | Database port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost` | Allowed CORS origins (comma-separated) |
| `GOOGLE_OAUTH_REDIRECT_URI` | `http://localhost/auth/callback/google` | OAuth callback URL |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile (dev/prod) |

## Docker Network Architecture

### Network Topology

```
┌─────────────────────────────────────────────────────┐
│ Host Machine                                         │
│                                                      │
│  ┌─────────────────────────────────────────────┐   │
│  │ Docker Bridge Network (172.18.0.0/16)       │   │
│  │                                              │   │
│  │  ┌──────────┐   ┌───────────┐   ┌────────┐ │   │
│  │  │  nginx   │   │  backend  │   │  mysql │ │   │
│  │  │ :80      │───│ :8080     │───│ :3306  │ │   │
│  │  └──────────┘   └───────────┘   └────────┘ │   │
│  │       │                                      │   │
│  └───────┼──────────────────────────────────────┘   │
│          │                                           │
│  Port Mapping: 80:80                                 │
└──────────┼──────────────────────────────────────────┘
           │
      Internet/Users
```

### Container Communication

- **nginx → backend**: Via hostname `backend:8080` (internal DNS)
- **backend → mysql**: Via hostname `mysql:3306` (internal DNS)
- **Host → nginx**: Via port mapping `localhost:80`

### IP Address Assignment

Containers get dynamic IPs from `172.18.0.0/16` subnet:
- nginx: typically `172.18.0.2`
- backend: typically `172.18.0.3`
- mysql: typically `172.18.0.4`

**Important**: Don't hardcode container IPs, use hostnames.

## Rate Limiting Configuration

### How It Works

1. **Client Request** → nginx (port 80)
2. nginx forwards request with headers:
   - `X-Forwarded-For`: Client's real IP
   - `X-Device-ID`: Browser cookie (if present)
   - `CF-Connecting-IP`: Cloudflare client IP (if using Cloudflare)
3. **Backend** (ClientIpResolver.java):
   - Checks if IP is private (10.x, 172.16-31.x, 192.168.x, 127.x)
   - If **public IP** → uses IP for rate limiting (`ip:203.0.113.1:auth`)
   - If **private IP** (Docker NAT) → falls back to device ID (`device:abc123:auth`)
4. **Rate Limiter** creates isolated buckets per identifier

### Private IP Detection (RFC 1918)

The system detects these as private (Docker NAT):
- `10.0.0.0` - `10.255.255.255`
- `172.16.0.0` - `172.31.255.255` (includes Docker's `172.18.0.0/16`)
- `192.168.0.0` - `192.168.255.255`
- `127.0.0.0` - `127.255.255.255` (localhost)
- `::1` (IPv6 localhost)

### Trusted Proxy Configuration

**Critical**: `TRUSTED_PROXY_IPS` must include nginx container IP range.

```bash
# Development (Docker only)
TRUSTED_PROXY_IPS=172.18.0.0/16

# Production (Docker + Cloudflare)
TRUSTED_PROXY_IPS=172.18.0.0/16,103.21.244.0/22,103.22.200.0/22,103.31.4.0/22,104.16.0.0/13,108.162.192.0/18,131.0.72.0/22,141.101.64.0/18,162.158.0.0/15,172.64.0.0/13,173.245.48.0/20,188.114.96.0/20,190.93.240.0/20,197.234.240.0/22,198.41.128.0/17
```

**Why This Matters**:
- If nginx IP not trusted → backend rejects `X-Forwarded-For` header
- All requests appear from nginx IP (172.18.0.x)
- All users share one rate limit bucket (DoS vulnerability)

### Rate Limit Buckets

| Endpoint Type | Bucket Name | Limit | Refill Period |
|---------------|-------------|-------|---------------|
| Authentication | `identifier:auth` | 10 requests | 1 minute |
| Planner CRUD | `identifier:planner` | 30 requests | 1 minute |
| Comments | `identifier:comment` | 10 requests | 1 minute |

**Bucket Key Format**: `<identifier>:<endpoint-type>`
- Examples: `ip:203.0.113.1:auth`, `device:abc-123:planner`

## OAuth Configuration

### Google OAuth Setup

1. **Create OAuth Client**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
   - Create OAuth 2.0 Client ID (Web application)
   - Note Client ID and Secret

2. **Configure Redirect URIs**:
   - Development: `http://localhost/auth/callback/google`
   - Production: `https://yourdomain.com/auth/callback/google`
   - **Important**: Must match `GOOGLE_OAUTH_REDIRECT_URI` in `.env`

3. **Enable Required APIs**:
   - Google+ API (for user profile)
   - People API (optional, for extended profile)

### Environment Variables

```bash
# Get from Google Cloud Console
GOOGLE_OAUTH_CLIENT_ID=123456789-abcdefg.apps.googleusercontent.com
GOOGLE_OAUTH_CLIENT_SECRET=GOCSPX-your-secret-here

# Must match Google Console configuration
GOOGLE_OAUTH_REDIRECT_URI=http://localhost/auth/callback/google
```

### OAuth Flow

```
User clicks "Sign in with Google"
  ↓
Frontend redirects to Google
  ↓
User authorizes
  ↓
Google redirects to /auth/callback/google?code=...
  ↓
Backend exchanges code for tokens
  ↓
Backend retrieves user profile
  ↓
Backend creates/updates user in database
  ↓
Backend generates JWT tokens
  ↓
Backend sets HttpOnly cookies
  ↓
User authenticated
```

## CORS Configuration

### How CORS Works

1. Browser makes preflight `OPTIONS` request
2. nginx forwards to backend
3. Backend (CorsConfig.java) sets headers:
   - `Access-Control-Allow-Origin`: Allowed origins
   - `Access-Control-Allow-Credentials`: true (for cookies)
   - `Access-Control-Allow-Methods`: GET, POST, PUT, DELETE
4. nginx **passes through** (doesn't add duplicate headers)
5. Browser receives CORS headers, allows request

### Environment Variables

```bash
# Development
CORS_ALLOWED_ORIGINS=http://localhost

# Production (single origin)
CORS_ALLOWED_ORIGINS=https://yourdomain.com

# Production (multiple origins)
CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com
```

**Important**:
- No trailing slash: `http://localhost` ✓, `http://localhost/` ✗
- Include protocol: `https://domain.com` ✓, `domain.com` ✗
- Comma-separated for multiple origins

### Common CORS Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| "CORS policy: No 'Access-Control-Allow-Origin'" | Wrong origin | Check `CORS_ALLOWED_ORIGINS` |
| "Credentials flag is 'true', but 'Access-Control-Allow-Credentials' is ''" | Missing credentials | Ensure backend sets credentials:true |
| Duplicate CORS headers | nginx adds headers | nginx should only proxy, not add CORS |

## Database Configuration

### MySQL Environment Variables

```bash
# Root user (for admin tasks)
MYSQL_ROOT_PASSWORD=<strong-password>

# Application user (used by backend)
MYSQL_USER=danteplanner
MYSQL_PASSWORD=<app-password>
MYSQL_DATABASE=danteplanner
```

### Database Initialization

On first start, MySQL container:
1. Creates database: `MYSQL_DATABASE`
2. Creates user: `MYSQL_USER` with `MYSQL_PASSWORD`
3. Grants all privileges on `MYSQL_DATABASE` to `MYSQL_USER`

Backend (Spring Boot) then:
4. Connects as `MYSQL_USER`
5. Runs Flyway migrations (V001__*, V002__*, etc.)
6. Creates tables, indexes, constraints

### Connection String

Backend uses:
```
jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

Parameters:
- `useSSL=false`: Disable SSL for Docker (encrypted in production)
- `serverTimezone=UTC`: Set timezone to UTC
- `allowPublicKeyRetrieval=true`: Allow public key retrieval for authentication

### Data Persistence

MySQL data stored in Docker volume: `mysql-data`

```bash
# List volumes
docker volume ls

# Inspect volume
docker volume inspect limbusplanner_mysql-data

# Backup volume
docker run --rm -v limbusplanner_mysql-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/mysql-backup.tar.gz /data

# Restore volume
docker run --rm -v limbusplanner_mysql-data:/data -v $(pwd):/backup \
  alpine tar xzf /backup/mysql-backup.tar.gz -C /
```

## Troubleshooting

### Issue: Backend can't connect to MySQL

**Symptoms**:
- Backend logs: "Unknown MySQL server host 'mysql'"
- Backend exits with error

**Diagnosis**:
```bash
# Check if MySQL container is running
docker-compose ps mysql

# Check MySQL logs
docker-compose logs mysql

# Test MySQL connectivity from backend container
docker-compose exec backend ping mysql
```

**Fix**:
- Ensure `MYSQL_HOST=mysql` (Docker service name)
- Wait for MySQL health check to pass (30-60 seconds)
- Check `docker-compose.yml` has `depends_on: mysql: condition: service_healthy`

### Issue: Rate limiting not isolating users

**Symptoms**:
- All users hit rate limit simultaneously
- Rate limit shared across multiple devices

**Diagnosis**:
```bash
# Check trusted proxy config
docker-compose exec backend env | grep TRUSTED_PROXY_IPS

# Test with different IPs
curl -H "X-Forwarded-For: 203.0.113.1" http://localhost/api/planner/md/published
curl -H "X-Forwarded-For: 203.0.113.2" http://localhost/api/planner/md/published
# Should have separate rate limit buckets
```

**Fix**:
- Ensure `TRUSTED_PROXY_IPS=172.18.0.0/16`
- Check nginx forwards `X-Forwarded-For` header (nginx.conf)
- Verify ClientIpResolver detects private IPs correctly

### Issue: OAuth redirect fails

**Symptoms**:
- "Redirect URI mismatch" error
- OAuth callback returns 400

**Diagnosis**:
```bash
# Check OAuth redirect URI
docker-compose exec backend env | grep GOOGLE_OAUTH_REDIRECT_URI

# Check Google Cloud Console configuration
# Should match exactly (including protocol and port)
```

**Fix**:
- Development: Use `http://localhost/auth/callback/google`
- Production: Use `https://yourdomain.com/auth/callback/google`
- Ensure Google Console has matching redirect URI

### Issue: CORS errors in browser console

**Symptoms**:
- "Access to fetch at ... has been blocked by CORS policy"
- API requests fail in browser

**Diagnosis**:
```bash
# Check CORS config
docker-compose exec backend env | grep CORS_ALLOWED_ORIGINS

# Test with curl
curl -H "Origin: http://localhost" -I http://localhost/api/health
# Should have Access-Control-Allow-Origin header
```

**Fix**:
- Ensure `CORS_ALLOWED_ORIGINS` matches frontend origin
- Restart backend after changing: `docker-compose restart backend`
- Check nginx doesn't add duplicate CORS headers

### Issue: Docker build fails

**Symptoms**:
- "maven dependency resolution failed"
- "yarn install failed"

**Diagnosis**:
```bash
# Check build logs
docker-compose up --build backend 2>&1 | tail -50
docker-compose up --build nginx 2>&1 | tail -50
```

**Fix**:
- Check internet connectivity
- Clear Docker cache: `docker-compose build --no-cache`
- Increase Docker memory limit (Settings → Resources)

## Security Checklist

- [ ] `.env` file added to `.gitignore`
- [ ] Strong passwords generated (32+ bytes)
- [ ] `TRUSTED_PROXY_IPS` restricted to known proxies only
- [ ] `CORS_ALLOWED_ORIGINS` set to specific domain (not *)
- [ ] MySQL root password different from app password
- [ ] JWT_SECRET rotated periodically
- [ ] Google OAuth credentials kept confidential
- [ ] Production uses HTTPS (SSL/TLS certificate)
- [ ] Database backups scheduled
- [ ] Container images updated regularly

## Next Steps

- [Architecture Map](../architecture-map.md) - System architecture
- [Backend Patterns](../../backend/CLAUDE.md) - Backend development
