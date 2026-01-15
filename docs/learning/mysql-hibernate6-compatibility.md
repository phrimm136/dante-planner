# MySQL and Hibernate 6 Compatibility: UUID Storage and Migration Syntax

**Date**: 2026-01-16
**Context**: Debugging planner sync validation errors in Docker deployment

---

## Executive Summary

When deploying a Spring Boot application with Hibernate 6 and MySQL, several compatibility issues arise from:
1. **Hibernate 6 default UUID handling** - sends UUIDs as BINARY(16) instead of CHAR(36)
2. **PostgreSQL-specific SQL syntax** in Flyway migrations that fails on MySQL
3. **MySQL AUTO_INCREMENT behavior** with id=0 insertion
4. **Docker volume mounting** for static data files

This document covers the diagnosis process, root causes, and fixes for each issue.

---

## Issue 1: UUID Binary vs String Storage

### Symptom
```
java.sql.SQLException: Incorrect string value: '\x9CL\xF4\x0C\x06N...' for column 'id' at row 1
```

### Root Cause
Hibernate 6 changed the default UUID storage strategy. Previously UUIDs were stored as strings (CHAR(36)), but Hibernate 6 stores them as binary (BINARY(16)) by default.

When your migration creates a `CHAR(36)` column but Hibernate sends binary data, MySQL throws a charset error.

### Solutions

**Option A: Force String Storage (if existing data uses CHAR(36))**
```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Id
@Column(columnDefinition = "CHAR(36)")
@JdbcTypeCode(SqlTypes.CHAR)  // Force string storage
private UUID id;
```

**Option B: Use Binary Storage (recommended for new projects)**
```java
@Id
@Column(columnDefinition = "BINARY(16)")
private UUID id;  // Hibernate 6 default behavior
```

Migration:
```sql
CREATE TABLE planners (
    id BINARY(16) PRIMARY KEY,
    -- ...
);
```

### Trade-offs

| Aspect | CHAR(36) | BINARY(16) |
|--------|----------|------------|
| Storage | 36 bytes | 16 bytes |
| Readability | Human-readable in SQL tools | Requires conversion |
| Index Performance | Slower | Faster |
| Join Performance | Slower | Faster |

**Recommendation**: Use BINARY(16) for new projects. The storage and performance benefits outweigh readability concerns.

---

## Issue 2: PostgreSQL-Specific Migration Syntax

### Symptom
```
Caused by: org.flywaydb.core.internal.exception.FlywayMigrateException:
Schema `danteplanner` contains a failed migration to version 013 !
```

### Root Causes and Fixes

#### 2a. TIMESTAMP WITH TIME ZONE

**PostgreSQL syntax (fails on MySQL):**
```sql
ALTER TABLE users ADD COLUMN timeout_until TIMESTAMP WITH TIME ZONE;
```

**MySQL-compatible:**
```sql
ALTER TABLE users ADD COLUMN timeout_until TIMESTAMP;
```

MySQL stores TIMESTAMP in UTC internally; timezone handling is done at connection level.

#### 2b. Partial Indexes (WHERE clause)

**PostgreSQL syntax (fails on MySQL):**
```sql
CREATE INDEX idx_users_timeout_until ON users(timeout_until)
WHERE timeout_until IS NOT NULL;
```

**MySQL-compatible:**
```sql
CREATE INDEX idx_users_timeout_until ON users(timeout_until);
```

MySQL 8.0+ supports functional indexes but not partial indexes with WHERE clauses.

#### 2c. Descending Index Columns

**PostgreSQL/MySQL 8.0+ (works):**
```sql
CREATE INDEX idx_name ON table(column DESC);
```

This is actually valid in MySQL 8.0+. If you see issues, check for reserved word conflicts.

#### 2d. Reserved Words as Column Names

**Problem:**
```sql
CREATE INDEX idx ON notifications (user_id, deleted_at, read, created_at);
-- `read` is a reserved word in MySQL
```

**Fix:**
```sql
CREATE INDEX idx ON notifications (user_id, deleted_at, `read`, created_at);
```

---

## Issue 3: MySQL AUTO_INCREMENT with id=0

### Symptom
Sentinel user created with id=1 instead of id=0.

```sql
INSERT INTO users (id, email, ...) VALUES (0, '[deleted]', ...);
-- Result: User created with id=1
```

### Root Cause
MySQL's `NO_AUTO_VALUE_ON_ZERO` SQL mode controls whether 0 is treated as "generate next auto value" or as a literal 0.

By default, inserting 0 into an AUTO_INCREMENT column generates the next value.

### Fix
```sql
SET @old_sql_mode = @@sql_mode;
SET sql_mode = 'NO_AUTO_VALUE_ON_ZERO';
INSERT INTO users (id, email, provider, provider_id, created_at, updated_at)
VALUES (0, '[deleted]', 'system', 'DELETED_USER_SENTINEL', NOW(), NOW());
SET sql_mode = @old_sql_mode;
```

---

## Issue 4: Docker Volume for Static Data

### Symptom
```
Game data loaded - identities: 0, egos: 0, gifts: 0, themePacks: 0
```

Backend validation fails because GameDataRegistry can't load JSON files.

### Root Cause
The backend JAR is built with a relative path reference (`../static/data`) which doesn't exist inside the Docker container.

### Fix
Mount the static data as a volume in `docker-compose.yml`:
```yaml
backend:
  volumes:
    - ./static/data:/app/data:ro
  environment:
    GAME_DATA_PATH: /app/data
```

The `:ro` flag makes it read-only, preventing accidental modifications.

---

## Issue 5: Auth Token Refresh Infinite Loop

### Symptom
Browser console shows infinite requests:
```
GET /api/auth/me → 401
POST /api/auth/refresh → 401
GET /api/auth/me → 401
...
```

### Root Cause
Frontend code redirected to `/` when token refresh failed:
```typescript
if (!response.ok) {
  window.location.href = '/';  // Page reload triggers auth check again
  throw new Error('Token refresh failed');
}
```

### Fix
Remove the redirect; let the error propagate so the app enters guest mode:
```typescript
if (!response.ok) {
  throw new Error('Token refresh failed');  // No redirect
}
```

---

## Debugging Checklist for Docker Deployments

1. **Check Docker logs for actual error**
   ```bash
   docker logs --tail 100 container-name 2>&1 | grep -E "ERROR|Exception"
   ```

2. **Verify Flyway migration history**
   ```bash
   docker exec mysql-container mysql -uuser -ppass -e \
     "SELECT version, success FROM flyway_schema_history WHERE success=0;"
   ```

3. **Check if static files are accessible**
   ```bash
   docker exec backend-container ls -la /app/data/
   ```

4. **Verify database schema matches entity**
   ```bash
   docker exec mysql-container mysql -uuser -ppass -e \
     "DESCRIBE table_name;"
   ```

5. **Rebuild Docker image after code changes**
   ```bash
   docker compose build --no-cache backend
   docker compose up -d backend
   ```

---

## Key Learnings

1. **Hibernate 6 UUID default changed** - Always explicitly declare UUID storage strategy
2. **Test migrations on target database** - PostgreSQL and MySQL have significant syntax differences
3. **Docker images cache code** - `docker compose up` doesn't rebuild; use `docker compose build`
4. **Flyway checksums are immutable** - Modifying existing migrations requires DB reset or `flyway repair`
5. **MySQL reserved words** - Always check column names against MySQL reserved word list
6. **AUTO_INCREMENT edge cases** - Inserting id=0 requires `NO_AUTO_VALUE_ON_ZERO`
