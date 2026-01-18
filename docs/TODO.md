- 빌드 타임 CI script로 JSON 스키마 일치 확인
- Filter bookmark/favorites feature (save filter presets to localStorage, like haneuk.info)

## Temporarily Disabled Features

### FEATURE-001: Comment Report System (DISABLED)
**Reason**: Admin review interface not yet implemented
**Disabled**: 2026-01-18
**What's ready**:
- Backend: `CommentReportService`, `PlannerCommentReport` entity, `CommentReportRepository`
- Frontend: `useReportComment` mutation hook, report schemas/types
- Database: `planner_comment_reports` table (V026 migration)

**What's missing**:
- [ ] Admin interface to review reported comments
- [ ] Admin action endpoints (dismiss, delete comment, warn user)
- [ ] Notification to admins on new reports

**Re-enable when**:
1. Admin review interface built
2. Uncomment `CommentController.reportComment()` endpoint
3. Uncomment report button in `CommentActionButtons.tsx`

## Error Tracking

## Security Issues

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

## Infrastructure

### INFRA-002: Cloudflare Image Transformation via URL
**Source**: https://developers.cloudflare.com/images/transform-images/transform-via-url/
**Severity**: LOW (Performance optimization)
**Problem**: All static images served at full resolution regardless of device. Mobile users download unnecessarily large images.

**Recommended Sizes** (Cloudflare):
| Device | Max Width | URL Pattern |
|--------|-----------|-------------|
| Desktop | 1920px | `/cdn-cgi/image/width=1920,fit=scale-down/{path}` |
| Tablet | 960px | `/cdn-cgi/image/width=960,fit=scale-down/{path}` |
| Mobile | 640px | `/cdn-cgi/image/width=640,fit=scale-down/{path}` |

**Current Architecture**:
- All image paths centralized in `frontend/src/lib/assetPaths.ts` (70+ helpers)
- No responsive images - single source, CSS sizing only
- All images already `.webp` format

**Implementation Options**:
1. **Minimal**: Add `cfImage(path, width)` wrapper - manually applied where needed
2. **Automatic**: Modify `get*Path()` functions to accept optional `size` parameter
3. **Smart**: Create `useCfImage(path)` hook that detects viewport width

**Constraint**: Transforms only work in production (on Cloudflare domain). Local dev needs bypass.

**Files to modify**:
- `frontend/src/lib/assetPaths.ts` - Add transformation wrapper
- `frontend/src/lib/constants.ts` - Add `IS_PRODUCTION` check or similar

**Pending decisions**:
- [ ] Choose implementation approach (minimal vs automatic vs smart)
- [ ] Decide which image categories benefit most (identity portraits vs small icons)
- [ ] Test bandwidth savings with real device testing

### INFRA-003: DB 계정 분리 + Soft Delete 강화
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
