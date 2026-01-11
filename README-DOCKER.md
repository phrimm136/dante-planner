# Docker Quick Start Guide

This guide covers running LimbusPlanner with Docker Compose for both local development and production deployment.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+
- **2GB+ available RAM** (optimized configuration)
- 10GB+ available disk space

### Memory Requirements

The docker-compose.yml is optimized for **2GB RAM systems** (AWS t3.small):

| Service | Memory Limit | Memory Reserve | Purpose |
|---------|--------------|----------------|---------|
| MySQL | 512MB | 256MB | Database with reduced buffer pool |
| Backend | 1GB | 512MB | Spring Boot JVM with heap limit |
| nginx | 128MB | 64MB | Reverse proxy |
| **Total** | **~1.6GB** | **~832MB** | Leaves room for OS (~400MB) |

**AWS Instance Recommendations:**
- **t3.micro (1GB)**: ❌ Not enough (use RDS instead)
- **t3.small (2GB)**: ✅ Works with optimized config
- **t3.medium (4GB)**: ✅ Comfortable, recommended for production

## Quick Start (Local Development)

### 1. Clone and Navigate

```bash
git clone https://github.com/yourusername/LimbusPlanner.git
cd LimbusPlanner
```

### 2. Configure Environment Variables

```bash
# Copy the example file
cp .env.example .env

# Generate secure secrets
JWT_SECRET=$(openssl rand -base64 48)
MYSQL_ROOT_PASSWORD=$(openssl rand -base64 32)
MYSQL_PASSWORD=$(openssl rand -base64 32)

# Edit .env with your favorite editor
nano .env
```

**Required Variables:**
- `JWT_SECRET` - JWT signing key (generate with openssl above)
- `MYSQL_ROOT_PASSWORD` - MySQL root password
- `MYSQL_DATABASE` - Database name (default: danteplanner)
- `MYSQL_USER` - Database user (default: danteplanner)
- `MYSQL_PASSWORD` - Database password (generate with openssl above)
- `GOOGLE_OAUTH_CLIENT_ID` - Get from Google Cloud Console
- `GOOGLE_OAUTH_CLIENT_SECRET` - Get from Google Cloud Console
- `TRUSTED_PROXY_IPS` - **REQUIRED:** Set to `172.18.0.0/16` for Docker

### 3. Update Google OAuth Configuration

1. Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Select your OAuth 2.0 Client ID
3. Add authorized redirect URI: `http://localhost/auth/callback/google`
4. Save changes

### 4. Start Services

```bash
# Build and start all services
docker-compose up --build

# Or run in background
docker-compose up --build -d
```

### 5. Verify Deployment

```bash
# Check service health
docker-compose ps

# Check logs
docker-compose logs backend
docker-compose logs nginx

# Test health endpoint
curl http://localhost/api/actuator/health

# Expected output: {"status":"UP"}
```

### 6. Access Application

- **Frontend**: http://localhost
- **Backend API**: http://localhost/api
- **Health Check**: http://localhost/api/actuator/health

## Common Operations

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f backend
docker-compose logs -f nginx
docker-compose logs -f mysql
```

### Restart Services

```bash
# Restart all
docker-compose restart

# Restart specific service
docker-compose restart backend
```

### Stop Services

```bash
# Stop but keep containers
docker-compose stop

# Stop and remove containers
docker-compose down

# Stop and remove everything (including volumes)
docker-compose down -v
```

### Database Access

```bash
# Connect to MySQL
docker-compose exec mysql mysql -u root -p

# Run SQL file
docker-compose exec -T mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} danteplanner < backup.sql
```

### Rebuild After Code Changes

```bash
# Rebuild backend
docker-compose up --build backend

# Rebuild frontend
docker-compose up --build nginx
```

## Production Deployment

### 1. Environment Configuration

Create production `.env` file:

```bash
# Database (use managed database service recommended)
MYSQL_HOST=your-production-db-host
MYSQL_PORT=3306
MYSQL_DATABASE=danteplanner
MYSQL_USER=danteplanner
MYSQL_PASSWORD=<secure-password>

# JWT
JWT_SECRET=<strong-secret-key>

# OAuth
GOOGLE_OAUTH_CLIENT_ID=<production-client-id>
GOOGLE_OAUTH_CLIENT_SECRET=<production-secret>
GOOGLE_OAUTH_REDIRECT_URI=https://yourdomain.com/auth/callback/google

# CORS
CORS_ALLOWED_ORIGINS=https://yourdomain.com

# Trusted Proxies (Docker + Cloudflare)
TRUSTED_PROXY_IPS=172.18.0.0/16,103.21.244.0/22,103.22.200.0/22,103.31.4.0/22

# Spring Profile
SPRING_PROFILES_ACTIVE=prod
```

### 2. Update OAuth Redirect URI

Add production redirect URI to Google Cloud Console:
- `https://yourdomain.com/auth/callback/google`

### 3. Deploy with Production Profile

```bash
docker-compose -f docker-compose.yml up -d
```

### 4. Set Up Reverse Proxy (if using Cloudflare/nginx)

If running behind another reverse proxy, ensure it forwards:
- `X-Forwarded-For` header
- `X-Forwarded-Proto` header
- `CF-Connecting-IP` header (if using Cloudflare)
- `X-Device-ID` cookie

## Troubleshooting

### Backend Won't Start

**Symptom**: Backend container exits immediately

**Check**:
```bash
docker-compose logs backend
```

**Common Causes**:
1. MySQL not ready - Backend will retry, wait 30-60 seconds
2. Missing environment variables - Check `.env` file
3. Invalid JWT_SECRET - Must be long enough (32+ chars)
4. Database connection refused - Verify MySQL container is running

### OAuth Redirect Fails

**Symptom**: "Redirect URI mismatch" error from Google

**Fix**:
1. Check `GOOGLE_OAUTH_REDIRECT_URI` in `.env`
2. Ensure it matches Google Cloud Console configuration
3. Use `http://localhost/auth/callback/google` for dev
4. Use `https://yourdomain.com/auth/callback/google` for prod

### CORS Errors

**Symptom**: Browser shows "CORS policy" errors

**Fix**:
1. Check `CORS_ALLOWED_ORIGINS` in `.env`
2. Use `http://localhost` for dev
3. Use `https://yourdomain.com` for prod (no trailing slash)
4. Restart backend after changing: `docker-compose restart backend`

### Rate Limiting Not Working

**Symptom**: All users share same rate limit bucket

**Fix**:
1. Check `TRUSTED_PROXY_IPS` includes Docker network: `172.18.0.0/16`
2. Verify nginx forwards headers (check nginx logs)
3. Test with curl:
   ```bash
   curl -H "X-Forwarded-For: 203.0.113.1" \
        -H "X-Device-ID: test-device" \
        http://localhost/api/planner/md/published
   ```

### MySQL Data Lost After Restart

**Symptom**: Data missing after `docker-compose down`

**Fix**:
- Use `docker-compose stop` instead of `down` to preserve volumes
- For `down`, don't use `-v` flag: `docker-compose down` (keeps volumes)
- Backup before removing volumes: `docker-compose down -v` removes data

### Port 80 Already in Use

**Symptom**: "bind: address already in use" error

**Fix**:
1. Find process using port 80: `sudo lsof -i :80`
2. Stop conflicting service
3. Or change port in docker-compose.yml:
   ```yaml
   ports:
     - "8080:80"  # Access via http://localhost:8080
   ```

## Data Backup

### Export Database

```bash
docker-compose exec mysql mysqldump \
  -u root -p${MYSQL_ROOT_PASSWORD} \
  danteplanner > backup-$(date +%Y%m%d).sql
```

### Import Database

```bash
docker-compose exec -T mysql mysql \
  -u root -p${MYSQL_ROOT_PASSWORD} \
  danteplanner < backup-20260111.sql
```

## Performance Tuning

The default docker-compose.yml is **already optimized for 2GB systems**.

### For Systems with 4GB+ RAM

If you have more memory available, you can increase limits:

**Edit `docker-compose.yml`:**
```yaml
mysql:
  command:
    - --innodb-buffer-pool-size=512M  # Increase from 256M
    - --max-connections=100           # Increase from 50
  deploy:
    resources:
      limits:
        memory: 1G                    # Increase from 512M

backend:
  environment:
    JAVA_OPTS: "-Xms512m -Xmx1536m -XX:MaxMetaspaceSize=384m"  # Increase heap
  deploy:
    resources:
      limits:
        memory: 2G                    # Increase from 1G
```

### For 1GB Systems (t3.micro) - Use AWS RDS

Cannot run full stack. Use managed database:

1. Create RDS MySQL instance (db.t3.micro free tier)
2. Modify docker-compose.yml to remove `mysql` service
3. Set `MYSQL_HOST` to RDS endpoint
4. Reduces memory usage to ~1.1GB (backend + nginx only)

## Monitoring

### Health Checks

```bash
# Backend health
curl http://localhost/api/actuator/health

# Metrics
curl http://localhost/api/actuator/metrics
```

### Memory Usage

Monitor container memory in real-time:

```bash
# All containers
docker stats

# Specific container
docker stats danteplanner-backend
```

**Expected memory usage (optimized config):**
- MySQL: ~300-400MB
- Backend: ~600-800MB
- nginx: ~10-20MB
- **Total**: ~1.0-1.2GB (well within 2GB systems)

**Warning signs:**
- MySQL using >500MB → Reduce `innodb-buffer-pool-size`
- Backend using >900MB → Reduce `JAVA_OPTS -Xmx` value
- Any container showing OOMKilled → Increase system RAM or reduce limits

### Disk Usage

```bash
# Volume usage
docker system df -v

# Clean unused volumes
docker volume prune
```

### Performance Monitoring

```bash
# CPU and memory in real-time
docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"

# Check for memory pressure
docker inspect danteplanner-backend | grep -A 5 "Memory"
```

## Security Notes

1. **Never commit `.env` file** - It contains secrets
2. **Use strong passwords** - Generate with openssl rand
3. **Rotate secrets periodically** - Especially JWT_SECRET
4. **Restrict TRUSTED_PROXY_IPS** - Only include known proxies
5. **Use HTTPS in production** - Set up SSL/TLS certificate
6. **Update base images** - Run `docker-compose pull` regularly

## Further Reading

- [Environment Setup Guide](docs/14-deploy/environment-setup.md) - Detailed configuration
- [Architecture Map](docs/architecture-map.md) - System architecture
- [Backend Patterns](backend/CLAUDE.md) - Backend development guidelines

## Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/LimbusPlanner/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/LimbusPlanner/discussions)
