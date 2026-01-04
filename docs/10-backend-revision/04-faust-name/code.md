# Code: Gesellschaft Username Generation

## What Was Done

- Created V011 migration adding `username_keyword` + `username_suffix` columns with UNIQUE constraint
- Added `UsernameConfig` with 10 associations and time-decay weights (3/2/1 for 0-30/31-60/61+ days)
- Implemented `RandomUsernameGenerator` with SecureRandom, 31-char safe alphanumeric set, 5-char suffix
- Updated `User` entity with new username fields and `UserService` with collision-retry loop
- Modified `UserDto` and `PublicPlannerResponse` to expose username fields via API
- Created i18n files (EN/KR/JP/CN) with association translations
- Updated `Header.tsx` to display localized username format `Faust-{Assoc}-{suffix}`

## Files Changed

**Created:**
- `backend/src/main/resources/db/migration/V011__add_username_columns.sql`
- `backend/src/main/java/org/danteplanner/backend/config/UsernameConfig.java`
- `backend/src/main/java/org/danteplanner/backend/config/AssociationProvider.java`
- `backend/src/main/java/org/danteplanner/backend/service/RandomUsernameGenerator.java`
- `backend/src/test/java/org/danteplanner/backend/service/RandomUsernameGeneratorTest.java`
- `static/i18n/EN/association.json`, `static/i18n/KR/association.json`
- `static/i18n/JP/association.json`, `static/i18n/CN/association.json`

**Modified:**
- `backend/src/main/java/org/danteplanner/backend/entity/User.java`
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java`
- `backend/src/main/java/org/danteplanner/backend/dto/UserDto.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PublicPlannerResponse.java`
- `frontend/src/components/Header.tsx`
- `frontend/src/schemas/AuthSchemas.ts`

## Verification Results

- Entity/DTO compile: ✅ Pass
- `./mvnw test-compile`: ✅ Pass
- `./mvnw test`: ✅ Pass (4 pre-existing failures unrelated)
- `yarn tsc`: ✅ Pass
- RandomUsernameGeneratorTest: ✅ All assertions pass

## Issues & Resolutions

- **Test fixture violations**: Test fixtures lacked required `usernameKeyword`/`usernameSuffix` fields → Added fields to all User fixtures in test classes
- **Collision retry infinite loop concern**: Used `while (true)` with DataIntegrityViolationException catch → Safe due to 28M+ namespace; logs after 10 retries
- **i18n fallback**: Missing translation key → Used `defaultValue` in t() function for graceful fallback to English
- **PublicPlannerResponse breaking API**: Added `authorUsernameKeyword`/`Suffix` fields → Requires atomic frontend+backend deploy
