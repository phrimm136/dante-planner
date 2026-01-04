# Task: Gesellschaft Username Generation

## Description

Implement auto-generated immutable usernames for new users using a "Gesellschaft" (company/association) theme. All users become "employees" of the Faust wiki with company-style IDs.

**Format**: `Faust-{Association}-{5-char suffix}`
- **Faust**: Always "Faust" (Title Case) - fixed prefix for all users
- **Association**: Random selection from Faust's identity associations (Title Case)
- **Suffix**: 5-character safe alphanumeric, lowercase

**Examples**: `Faust-WCorp-7k3mx`, `Faust-Butler-x2p9n`, `Faust-Heishou-3n8km`

### Username Generation Behavior

1. **Timing**: Generated once on first OAuth login (immutable - users cannot change)
2. **Association Selection**: Weighted random with time-decay:
   - Days 0-30 since added: 3x weight (boost new associations)
   - Days 31-60: 2x weight (tapering)
   - Days 61+: 1x weight (normalized baseline)
3. **Collision Handling**:
   - Database UNIQUE constraint on suffix (globally unique)
   - Retry up to 20 times on collision
   - ~28.6 million namespace (31^5) makes collision extremely rare

### Suffix Character Set

31 safe alphanumeric characters (excludes ambiguous: 0, 1, O, I, L):
```
2-9, a-k, m-n, p-z
```

### Display Name Resolution

**Two-layer system for flexibility and i18n:**

1. **Backend Config** (`UsernameConfig.java`):
   - Maps keyword → default English display name
   - Provides validation of valid keywords
   - Contains added dates for time-decay weighting

2. **Frontend i18n** (`static/i18n/{lang}/association.json`):
   - Maps keyword → translated display name per language
   - Enables localized display: `Faust-WCorp-7k3mx` (EN) → `파우스트-W사-7k3mx` (KR)

**Display Flow**:
```
DB: username_keyword="W_CORP", username_suffix="7k3mx"
    ↓
Backend API returns: { keyword: "W_CORP", suffix: "7k3mx" }
    ↓
Frontend i18n: t("association.W_CORP") → "WCorp" (EN) / "W사" (KR)
    ↓
Display: "Faust-WCorp-7k3mx" (EN) / "파우스트-W사-7k3mx" (KR)
```

### Association Curation

Associations are manually curated with roleplay-friendly display names:

| Keyword | Display Name (EN) | Notes |
|---------|-------------------|-------|
| `LIMBUS_COMPANY_LCB` | TBD | Base identity |
| `W_CORP` | WCorp or Cleanup | W Corp cleanup crews |
| `LOBOTOMY_BRANCH` | Lobotomy or Keter | L Corp abnormality theme |
| `N_CORP` | NCorp or Fanatic | N Corp nail theme |
| `ZWEI` | Zwei | Fixer guild |
| `SEVEN` | Seven | The Seven fixers |
| `SHI` | Shi | Eastern assassins |
| `WUTHERING_HEIGHTS` | **Butler** | Creative rename |
| `H_CORP` | **Heishou** | Creative rename |
| `BLADE_LINEAGE` | Blade | Blade Lineage clan |

**Note**: Final list requires curation by domain expert. Some associations may be renamed for roleplay flavor (e.g., "Butler" instead of "WutheringHeights").

## Research

- [ ] Review Faust's identity `unitKeywordList` values in `static/data/identity/102*.json`
- [ ] Curate final list of associations with roleplay-friendly display names
- [ ] Assign "added dates" for each association (for time-decay weighting)
- [ ] Review `SecureRandom` usage patterns in Java for cryptographic randomness
- [ ] Check existing i18n patterns in `static/i18n/{lang}/` for association.json structure

## Scope

**Files to READ for context:**
- `backend/src/main/java/org/danteplanner/backend/entity/User.java` - Current user entity
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java` - User creation flow
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` - OAuth flow
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PublicPlannerResponse.java` - Current "Anonymous" handling
- `frontend/src/components/Header.tsx` - Current user display
- `static/data/identity/102*.json` - Faust identity data with unitKeywordList
- `static/i18n/EN/common.json` - i18n structure reference

## Target Code Area

**Backend (CREATE):**
- `backend/src/main/resources/db/migration/V011__add_username_columns.sql`
- `backend/src/main/java/org/danteplanner/backend/service/RandomUsernameGenerator.java`
- `backend/src/main/java/org/danteplanner/backend/config/UsernameConfig.java`

**Backend (MODIFY):**
- `backend/src/main/java/org/danteplanner/backend/entity/User.java`
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PublicPlannerResponse.java`
- `backend/src/main/resources/application.properties`

**Frontend (MODIFY):**
- `frontend/src/components/Header.tsx`

**i18n (CREATE):**
- `static/i18n/EN/association.json`
- `static/i18n/KR/association.json`
- `static/i18n/JP/association.json`
- `static/i18n/CN/association.json`

## System Context (Senior Thinking)

- **Feature domain**: Authentication / User Management
- **Core files in this domain**:
  - `controller/AuthController.java`
  - `service/UserService.java`
  - `entity/User.java`
  - `repository/UserRepository.java`
- **Cross-cutting concerns touched**:
  - i18n (new association.json files)
  - Database schema (Flyway migration)
  - PublicPlannerResponse (replaces "Anonymous")
  - Frontend Header component

## Impact Analysis

**Files being modified:**

| File | Impact Level | What Depends On It |
|------|--------------|-------------------|
| `User.java` | Medium | All user-related queries and DTOs |
| `UserService.java` | Medium | AuthController, user creation flow |
| `PublicPlannerResponse.java` | Medium | All public planner endpoints |
| `Header.tsx` | Low | Page isolated |

**Database changes:**
- New columns: `username_keyword`, `username_suffix`
- New constraint: `UNIQUE(username_suffix)`
- No existing data migration needed (no real users yet)

**Potential ripple effects:**
- Any code expecting user without username fields needs handling
- PublicPlannerResponse consumers will receive keyword+suffix instead of "Anonymous"

## Risk Assessment

**Edge cases to handle:**
- Collision after 20 retries (extremely rare with 28.6M namespace - log error, fail gracefully)
- Null/empty keyword in display (fallback to "Anonymous")
- Missing i18n translation for keyword (fallback to English display name from backend config)

**Concurrency:**
- Two users registering simultaneously could get same suffix
- Database UNIQUE constraint is atomic - handles this via retry
- Use `DataIntegrityViolationException` catch and retry pattern

**Security considerations:**
- `SecureRandom` prevents suffix pattern prediction (no timing leak)
- Suffix doesn't reveal registration timestamp (unlike sequential numbers)
- Association reveals approximate registration era (acceptable - "generational" cohorts)

**Backward compatibility:**
- No existing users need migration (confirmed: no real user data)
- PublicPlannerResponse change requires frontend update in same release

## Database Schema

```sql
-- V011__add_username_columns.sql
ALTER TABLE users
ADD COLUMN username_keyword VARCHAR(50) NOT NULL,
ADD COLUMN username_suffix VARCHAR(5) NOT NULL;

ALTER TABLE users
ADD CONSTRAINT uk_users_username_suffix UNIQUE (username_suffix);

CREATE INDEX idx_users_username_keyword ON users(username_keyword);
```

## Testing Guidelines

### Manual UI Testing

1. Start backend and frontend locally
2. Clear browser cookies/storage to ensure fresh state
3. Click "Login with Google" button
4. Complete OAuth flow with test account
5. Verify username appears in Header dropdown (format: `Faust-{Association}-{suffix}`)
6. Note the username for verification
7. Open browser DevTools → Network tab
8. Navigate to published planners list page
9. Inspect API response for `/api/planner/md/published`
10. Verify `authorName` field contains username (not "Anonymous")
11. Change browser language to Korean
12. Verify username displays translated (e.g., `파우스트-W사-7k3mx`)
13. Log out and log in again
14. Verify same username persists (immutable)

### Automated Functional Verification

- [ ] Username generation: New user receives `Faust-{Association}-{suffix}` format username
- [ ] Suffix uniqueness: Database constraint prevents duplicate suffixes
- [ ] Suffix charset: Only contains characters from safe set (2-9, a-k, m-n, p-z)
- [ ] Suffix length: Exactly 5 characters
- [ ] Association validity: Keyword exists in UsernameConfig
- [ ] Immutability: Same user always returns same username
- [ ] Collision retry: Generation retries on duplicate suffix
- [ ] PublicPlannerResponse: Returns keyword + suffix (not "Anonymous")

### Edge Cases

- [ ] Collision handling: After 20 retries, throws descriptive exception
- [ ] Missing i18n: Falls back to English display name from backend config
- [ ] Invalid keyword in DB: Falls back to "Anonymous" display
- [ ] Concurrent registration: Both users get unique suffixes via DB constraint

### Integration Points

- [ ] OAuth flow: Username generated during `findOrCreateUser()`
- [ ] Header display: Shows translated username from i18n
- [ ] Public planners: Author name shows username instead of "Anonymous"
- [ ] i18n switching: Username translation updates on language change

## Implementation Notes

### Time-Decay Weight Calculation

```java
public int calculateWeight(LocalDate addedDate) {
    long daysOld = ChronoUnit.DAYS.between(addedDate, LocalDate.now());

    if (daysOld <= 30) return 3;  // Boost new associations
    if (daysOld <= 60) return 2;  // Tapering
    return 1;                      // Baseline
}
```

### Weighted Random Selection

```java
// Build weighted list: [LCB, LCB, LCB, WCorp, WCorp, Zwei, ...] based on weights
// Use SecureRandom to pick index
// This naturally gives 3x probability to weight-3 associations
```

### Frontend Display Helper

```typescript
function formatUsername(keyword: string, suffix: string, t: TFunction): string {
  const sinnerName = t('sinner.Faust');
  const assocName = t(`association.${keyword}`, { defaultValue: keyword });
  return `${sinnerName}-${assocName}-${suffix}`;
}
```
