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

## Infrastructure

### INFRA-001: Dockerize backend server
- [ ] Create `backend/Dockerfile` for Spring Boot app
- [ ] Create `docker-compose.yml` with MySQL service
- [ ] Use `env_file:` directive to load .env
- [ ] Add health checks and proper networking
- [ ] Document local development workflow