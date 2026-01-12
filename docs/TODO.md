- 빌드 타임 CI script로 JSON 스키마 일치 확인
- Filter bookmark/favorites feature (save filter presets to localStorage, like haneuk.info)

## Error Tracking

### ERR-001: Add Sentry error tracking to sanity condition formatter (PENDING)
**Source**: code-architecture-reviewer - sanity condition formatting feature
**Severity**: MEDIUM (Silent failures in production)
**Problem**: `formatSanityCondition.ts` uses `console.warn()` for missing i18n entries. Users won't report, data team won't see.
**Current behavior**: Falls back to raw function name with console warning only
**Solution**: Use `Sentry.captureMessage()` for missing i18n entries
**Files to modify**:
- `frontend/src/lib/formatSanityCondition.ts` - Replace console.warn with Sentry

## Security Issues

### SEC-001: Add random username generation (PENDING)
**Source**: code-architecture-reviewer - docs/07-planner-list/01-list-initialization
**Severity**: HIGH (Privacy violation)
**Problem**: PublicPlannerResponse.fromEntity() hardcodes "Anonymous" to prevent email exposure.
**Current behavior**: All users show as "Anonymous" - no identity.

**Solution**: Auto-generate immutable username on first login using Gesellschaft format.

**Format**: `Faust-{Association}-{5-char suffix}`
- Sinner: Always "Faust" (Title Case)
- Association: Random from Faust's identity associations (Title Case, manually curated)
- Suffix: 5-char safe alphanumeric, lowercase (excludes 0/1/O/I/L)
- Example: `Faust-WCorp-7k3mx`, `Faust-Butler-x2p9n`

**Database Schema** (two-column, normalized):
```sql
username_keyword VARCHAR(50) NOT NULL,  -- Canonical keyword: "W_CORP"
username_suffix  VARCHAR(5) NOT NULL,   -- Random suffix: "7k3mx"
UNIQUE(username_suffix)                  -- Globally unique suffix
```

**Display Name Extraction**:
- Backend: `UsernameConfig.java` - keyword → default English display name
- Frontend: `static/i18n/{lang}/association.json` - keyword → translated display name
- Flow: DB keyword → i18n lookup → "Faust-{translated}-{suffix}"

**Behavior**:
- Generated once on first login (immutable, no user changes)
- Weighted random selection for associations (time-decay: 3x→2x→1x over 60 days)
- Globally unique suffix + 20 retries for collision handling
- Translated display per user's language (Faust-WCorp → 파우스트-W사)
- Namespace: ~28.6 million combinations (31^5)

**Association Curation** (manual, roleplay-friendly names):
| Keyword | Display Name (EN) | Notes |
|---------|-------------------|-------|
| `LIMBUS_COMPANY_LCB` | TBD | Base identity |
| `W_CORP` | WCorp or Cleanup | |
| `WUTHERING_HEIGHTS` | Butler | Creative rename |
| `H_CORP` | Heishou | Creative rename |
| ... | ... | Curate full list |

**Files to modify**:
- `V011__add_username.sql` - Add username_keyword, username_suffix columns
- `User.java` - Add two fields + derived display method
- `RandomUsernameGenerator.java` - New service for generation
- `UsernameConfig.java` - Keyword→display mapping + dates for time-decay
- `UserService.java` - Call generator on findOrCreateUser()
- `PublicPlannerResponse.java` - Return keyword + suffix (frontend handles display)
- `Header.tsx` - Display translated username via i18n
- `static/i18n/{lang}/association.json` - New file for association translations

**Pending tasks**:
- [ ] Curate final list of Faust associations with roleplay-friendly display names
- [ ] Assign "added dates" for each association (time-decay weighting)
- [ ] Add i18n entries for all associations (EN, KR, JP, CN)
Extract common parts of seasonsDropdown.tsx and AssociationDropdown.md

## Architecture

### ARCH-001: PlannerController Field Injection (PENDING)
**Source**: code-architecture-reviewer - security issue handling (2026-01-04)
**Severity**: MEDIUM (Violates backend guidelines, reduces testability)
**Problem**: `PlannerController` uses `@Value` field injection instead of constructor injection.

**Current code** (lines 49-56):
```java
@Value("${planner.schema-version}")
private Integer schemaVersion;

@Value("${planner.md.current-version}")
private Integer mdCurrentVersion;

@Value("${planner.rr.available-versions}")
private String rrAvailableVersions;
```

**Why this matters**:
1. Violates backend guidelines: "Constructor injection only - No `@Autowired` field injection"
2. Untestable: Cannot mock these values without reflection
3. Circular dependency risk: Field injection can hide circular dependencies
4. Immutability lost: Fields are mutable (not `final`)

**Solution**: Create `PlannerProperties` class with `@ConfigurationProperties`:
```java
@Configuration
@ConfigurationProperties(prefix = "planner")
@Getter
public class PlannerProperties {
    private Integer schemaVersion;
    private Integer mdCurrentVersion;
    private String rrAvailableVersions;
}
```

Then inject via constructor in `PlannerController`.

**Files to modify**:
- `backend/src/main/java/org/danteplanner/backend/config/PlannerProperties.java` - New file
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` - Use constructor injection

## Deployment Configuration

### DEPLOY-001: Nginx X-Forwarded-For Configuration (UPDATED)
**Source**: code-architecture-reviewer - auth revision round 3
**Severity**: MEDIUM (Rate limit bypass if not behind trusted proxy)
**Status**: Partially addressed - `ClientIpResolver` now validates trusted proxy IPs

**Problem**: Rate limiting uses `X-Forwarded-For` header which can be spoofed without proper proxy config.

**Backend fix applied**: `ClientIpResolver` only trusts X-Forwarded-For when request comes from IPs in `security.trusted-proxy-ips`.

**Nginx config** (when deploying behind nginx):
```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Host $host;
}
```

**Spring Boot** (add to `application.properties`):
```properties
server.forward-headers-strategy=native
# Set to nginx container IP in production
security.trusted-proxy-ips=${TRUSTED_PROXY_IPS:127.0.0.1}
```

**Reference**: `ClientIpResolver.resolve()` validates trusted proxy before trusting header.

## Infrastructure

### INFRA-001: Dockerize backend server
- [ ] Create `backend/Dockerfile` for Spring Boot app
- [ ] Create `docker-compose.yml` with MySQL service
- [ ] Use `env_file:` directive to load .env
- [ ] Add health checks and proper networking
- [ ] Document local development workflow

### INFRA-002: DB 계정 분리 + Soft Delete 강화
**목적**: 앱 계정 DELETE 차단 + Soft Delete 패턴 완성

#### DB 계정 설정
- [ ] `app_user` 생성: SELECT, INSERT, UPDATE만 (DELETE 없음)
- [ ] `admin_user` 생성: ALL PRIVILEGES (콘솔 전용)

#### Entity 수정
- [ ] `Planner.java`: `@SQLDelete`, `@SQLRestriction("deleted_at IS NULL")` 추가
- [ ] `PlannerVote.java`: `@SQLDelete`, `@SQLRestriction` 추가
- [ ] `User.java`: `deletedAt` 필드 + Soft Delete 메서드 추가 (선택)
- [ ] Flyway 마이그레이션 작성 (User 변경 시)

#### 적용 제외
- `PlannerBookmark`: Hard Delete 유지 (북마크는 취소/재등록 패턴)

#### 테스트
- [ ] app_user DELETE 시도 → 권한 오류
- [ ] JPA delete() → UPDATE 변환 확인
- [ ] 삭제 데이터 자동 필터링 확인

## UX Improvements

### UX-001: Short ID for Planner URLs
**Source**: View count implementation discussion
**Severity**: LOW (UX improvement, not functional)
**Problem**: Current planner URLs use full UUID (`/planner/md/550e8400-e29b-41d4-a716-446655440000`), which is:
- Hard to share verbally or in chat
- Not memorable
- Ugly in browser address bar

**Solution**: Add `short_id` column with 8-10 character base62 encoded ID.
- Keep UUID as internal PK (database integrity)
- Use short_id in URLs: `/planner/md/xK9mN2pQ`
- Generate on planner creation using Nanoid or similar

**Industry Examples**:
- YouTube: `dQw4w9WgXcQ` (11 chars, base64-like)
- GitHub Gist: `aa5a315d61ae` (12 chars, hex)
- Notion: `page-slug-abc123` (slug + short ID)

**Files to modify**:
- `V009__add_planner_short_id.sql` - Add column with unique index
- `Planner.java` - Add shortId field, generate on creation
- `PlannerController.java` - Support lookup by shortId
- `frontend/src/lib/router.tsx` - Update route params
- All planner link components - Use shortId in URLs

**Migration**: Backfill existing planners with generated short IDs

### UX-002: Planner Views Table Cleanup Strategy
**Source**: View count implementation discussion
**Severity**: LOW (Long-term maintenance)
**Context**: INFRA-002 removes DELETE from app_user. The `planner_views` table uses date-based PK (no cleanup needed), but will grow indefinitely.

**Current Design**: PK = (planner_id, viewer_hash, view_date)
- ~1 row per viewer per day per planner
- At 10k MAU: ~1M rows/year estimate

**Future Options** (when table grows large):
1. **Soft Delete + Admin Purge**: Add `deleted_at`, scheduler marks old, admin hard-deletes
2. **MySQL Event**: DB-level scheduled cleanup with admin privileges
3. **Partitioning**: Partition by view_date, drop old partitions
4. **Archive Table**: Move old records to cold storage

**Action**: Monitor table size quarterly. Implement cleanup when approaching 10M rows.

## Testing

### TEST-002: Manual Verification - User Soft-Delete (PENDING)
**Source**: `docs/10-backend-revision/03-delete-user/instructions.md`
**Prerequisites**: Backend on `localhost:8080`, authenticated test user

**Delete Account Flow:**
- [ ] `DELETE /api/user/me` → 200 with `deletedAt` and `permanentDeleteAt` (30 days later)
- [ ] Auth with deleted user token → 401 with code `ACCOUNT_DELETED`
- [ ] Refresh token with deleted user → 401
- [ ] Sentinel user (id=0) exists in DB: `SELECT * FROM users WHERE id = 0`
- [ ] Scheduler cron registered in startup logs

**Reactivation Flow:**
- [ ] OAuth re-login during grace period → reactivates account
- [ ] Response includes `reactivated: true` flag
- [ ] User's planners still attributed to them

**Hard-Delete Flow (requires manual DB update):**
1. Soft-delete a user with planners and votes
2. Set `permanent_delete_scheduled_at` to past date in DB
3. Trigger scheduler (wait for 3 AM or restart with test cron)
4. Verify user record deleted, planners CASCADE deleted
5. Verify votes reassigned to sentinel user (user_id = 0)
6. OAuth re-login after hard-delete → new account created

### UX-003: Planner Conflict Merge Strategy (PENDING)
**Source**: Planner auto-save architecture research (2026-01-04)
**Severity**: LOW (Enhancement, not critical)
**Context**: Current conflict resolution offers 'overwrite' or 'discard'. A 'merge' option could preserve both sets of changes.

**Problem**: When 409 conflict occurs (server has newer version), user must choose:
- Overwrite: Lose server changes from other devices
- Discard: Lose local changes

**Merge Strategy Options**:
1. **Field-level merge**: Compare individual fields, auto-merge non-conflicting, prompt for conflicts
2. **Side-by-side view**: Show both versions, let user manually pick sections
3. **Three-way merge**: Use common ancestor (requires storing history)

**Complexity**: HIGH - Tiptap notes are nested JSON, equipment has complex structure
**ROI**: LOW - Conflicts are rare (single-device editing is common)

**If implemented**:
- Add `lastKnownSyncVersion` to detect which fields changed since last sync
- Store field-level timestamps for granular conflict detection
- Design merge UI for each section type (buffs, gifts, notes, equipment)

**Recommendation**: Defer until user feedback indicates conflicts are common.

### TEST-001: Run Planner View Count API Tests (PENDING)
**Source**: View count implementation (`docs/10-backend-revision/02-planner-view-count`)
**Script**: `test-view-count.sh` (already exists)
**Prerequisites**: Backend on `localhost:8080`, at least one published planner

**Test Cases:**
- MV1: First view increments count
- MV2: Duplicate same day no increment
- MV3: Different IP increments
- MV4: Unpublished returns 404

**Run**: `./test-view-count.sh`

### TEST-003: Manual Verification - Planner Editor Consolidation (PENDING)
**Source**: `docs/13-planner-editor/01-editor-initialization`
**Prerequisites**: Frontend on `localhost:5173`, Backend on `localhost:8080`, authenticated user

**New Mode (Unchanged Behavior):**
- [ ] MV1: Navigate to `/planner/md/new` → defaults load
- [ ] MV2: Draft recovery dialog appears with prior draft
- [ ] MV3: "Recover" restores changes, "Discard" clears
- [ ] MV4: Auto-save triggers after 2s debounce

**Edit Mode (New Feature):**
- [ ] MV5: Navigate to `/planner/md/{id}/edit` → planner loads
- [ ] MV6: NO draft recovery dialog appears
- [ ] MV7: State initialized from planner (title, category, equipment)
- [ ] MV8: Edit field → auto-save → refresh → changes persist

**Edge Cases:**
- [ ] MV9: Invalid UUID → 404 error with link to list
- [ ] MV10: Other user's planner → 404 from backend
- [ ] MV11: Concurrent edits → conflict dialog → reload server version

**Dependency Verification:**
- [ ] D1: PlannerCardContextMenu navigation still works
- [ ] D2: usePlannerSave unchanged behavior
- [ ] D3: New mode draft recovery unchanged
- [ ] D4: Progressive rendering works in both modes