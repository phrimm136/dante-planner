# Research: Gesellschaft Username Generation

## Clarifications Resolved

| Ambiguity | Decision |
|-----------|----------|
| Collision after retries | **Retry indefinitely** (28M namespace makes infinite loop impossible) |
| i18n key format | **Lowercase** (`w_corp`, `wuthering_heights`) |
| Default added date | **2024-01-01** (baseline weight 1x for existing associations) |
| Association curation | **Pending** - user to provide final list with roleplay names |

---

## Spec-to-Code Mapping

| Requirement | Files | Action |
|-------------|-------|--------|
| Store username | `User.java` | Add `usernameKeyword`, `usernameSuffix` fields |
| Generate on OAuth | `UserService.findOrCreateUser()` | Call `RandomUsernameGenerator` before save |
| Configure associations | `UsernameConfig.java` | New: keyword → display name + added date map |
| Collision handling | `RandomUsernameGenerator.java` | New: generate suffix, retry indefinitely on collision |
| Public planner display | `PublicPlannerResponse.java` | Replace "Anonymous" with formatted username |
| Frontend display | `Header.tsx` | Format username using i18n translation |
| Database schema | `V011__add_username_columns.sql` | New: columns + UNIQUE constraint |
| i18n translations | `static/i18n/{lang}/association.json` | New: 4 language files |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source |
|-------------|----------------|
| Username generation | `be-service` skill - service with `@Service`, constructor injection |
| Configuration | Static config class (similar to `PlannerContentValidator` size limits) |
| Collision retry | While loop + catch `DataIntegrityViolationException` |
| DTO modification | Existing `PublicPlannerResponse.fromEntity()` builder pattern |
| i18n structure | Flat object like `common.json`: `{ "w_corp": "WCorp" }` |
| Frontend formatting | Helper function using `t()` from `useTranslation()` |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `RandomUsernameGenerator.java` | `UserService.java` | Constructor injection, `@Service` |
| `UsernameConfig.java` | `PlannerContentValidator.java` | Static configuration constants |
| `V011__add_username_columns.sql` | `V010__*.sql` | Flyway naming, ALTER TABLE syntax |
| `association.json` | `common.json` | Flat JSON structure |

---

## Existing Utilities

| Category | Existing | Action |
|----------|----------|--------|
| Random generation | None | CREATE: `RandomUsernameGenerator.java` |
| Exception handling | `GlobalExceptionHandler` | REUSE: Handle `DataIntegrityViolationException` |
| DTO pattern | `PublicPlannerResponse` | EXTEND: Add username formatting |
| i18n | `common.json` structure | CREATE: `association.json` per language |

---

## Gap Analysis

**Create:**
- `RandomUsernameGenerator.java` - generation + collision retry
- `UsernameConfig.java` - association configuration
- `V011__add_username_columns.sql` - migration
- `static/i18n/{EN,KR,JP,CN}/association.json` - translations

**Modify:**
- `User.java` - add username fields
- `UserService.java` - call generator in `findOrCreateUser()`
- `PublicPlannerResponse.java` - format username instead of "Anonymous"
- `Header.tsx` - display translated username

**Reuse:**
- `SecureRandom` from Java stdlib
- `@Service` + `@Transactional` patterns
- `Builder` pattern from DTOs
- i18n structure from `common.json`

---

## Testing Requirements

### Manual UI Tests
1. Fresh OAuth login → verify `Faust-{Assoc}-{suffix}` in Header
2. Logout/login → verify same username persists
3. Switch language → verify username translates
4. Published planner → verify author shows username (not "Anonymous")

### Unit Tests (RandomUsernameGenerator)
- Suffix length exactly 5 chars
- Suffix charset only safe alphanumeric
- Weight calculation returns correct value by days
- Weighted selection favors newer associations

### Integration Tests (UserService)
- New user gets valid username
- Same user re-auth gets same username
- Collision retries until success
- `PublicPlannerResponse` includes username

---

## Technical Constraints

1. **SecureRandom required** - not `java.util.Random`
2. **DB UNIQUE on suffix** - atomic collision detection
3. **i18n keys lowercase** - `t('association.w_corp')`
4. **Retry indefinitely** - no max limit (namespace too large for infinite loop)
5. **Flyway immutable** - cannot edit applied migrations
6. **Same-release deploy** - backend + frontend must deploy together

---

## Pending: Association Curation

User must provide final list before implementation:

| Keyword (DB) | i18n Key | Display Name (EN) | Added Date |
|--------------|----------|-------------------|------------|
| `LIMBUS_COMPANY_LCB` | `limbus_company_lcb` | ? | 2024-01-01 |
| `W_CORP` | `w_corp` | WCorp or Cleanup? | 2024-01-01 |
| `WUTHERING_HEIGHTS` | `wuthering_heights` | Butler | 2024-01-01 |
| `H_CORP` | `h_corp` | Heishou | 2024-01-01 |
| ... | ... | ... | ... |

**Required decisions:**
- Which associations to include (10-15 recommended)
- Roleplay-friendly display names for each
- Any future associations with boosted dates
