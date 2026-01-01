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

### SEC-001: Add user display name field (PENDING)
**Source**: code-architecture-reviewer - docs/07-planner-list/01-list-initialization
**Severity**: HIGH (Privacy violation)
**Problem**: PublicPlannerResponse.fromEntity() extracts author name from email (before @). Current fallback logic would expose full email if malformed (no @ or @ at start).
**Current behavior**: Falls back to full email for malformed emails, then to "Anonymous"
**Solution**: Add `displayName` field to User entity. User sets their own username during signup or profile edit.
**Files to modify**:
- `User.java` - Add displayName field
- `V003__add_user_display_name.sql` - Migration
- `PublicPlannerResponse.fromEntity()` - Use user.getDisplayName() with fallback to "Anonymous"
**Blocked by**: Spec update for user display name feature
Extract common parts of seasonsDropdown.tsx and AssociationDropdown.md

## Deployment Configuration

### DEPLOY-001: Nginx X-Forwarded-For Configuration
**Source**: code-architecture-reviewer - auth revision round 3
**Severity**: MEDIUM (Rate limit bypass if not behind trusted proxy)
**Problem**: Rate limiting uses `X-Forwarded-For` header which can be spoofed without proper proxy config.

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
```

**Reference**: `AuthController.getClientIp()` uses this header for rate limiting.

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