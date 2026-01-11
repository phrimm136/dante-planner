# LimbusPlanner Architecture Map

> **Purpose:** Provide architectural context for AI-assisted development. Read this before diving into implementation details.
>
> **Last Updated:** 2026-01-11 (Planner voting simplified to upvote-only system)

---

## Quick Reference: Core Files by Feature

### Frontend Core Files

| Feature | Core Files | Supporting Files |
|---------|------------|------------------|
| **Identity Browser** | `routes/IdentityPage.tsx`, `routes/IdentityDetailPage.tsx` | `hooks/useIdentityListData.ts`, `hooks/useSearchMappings.ts`, `components/identity/*` |
| **EGO Browser** | `routes/EGOPage.tsx`, `routes/EGODetailPage.tsx` | `hooks/useEGOListData.ts`, `hooks/useEGODetailData.ts` (useEGODetailSpec, useEGODetailI18n), `hooks/useSearchMappings.ts`, `components/ego/*` (EGOHeaderI18n, SkillI18n, PassiveI18n for granular Suspense) |
| **EGO Gift Browser** | `routes/EGOGiftPage.tsx`, `routes/EGOGiftDetailPage.tsx` | `hooks/useEGOGiftListData.ts`, `hooks/useEGOGiftDetailData.ts` (useEGOGiftDetailSpec, useEGOGiftDetailI18n), `hooks/useSearchMappings.ts`, `hooks/useThemePackListData.ts` (useThemePackI18n), `lib/egoGiftFilter.ts`, `components/egoGift/*` (GiftNameI18n, EnhancementsPanelI18n for granular Suspense) |
| **Detail Page Layout** | `components/common/DetailPageLayout.tsx` | `DetailEntitySelector.tsx`, `DetailLeftPanel.tsx`, `DetailRightPanel.tsx`, `MobileDetailTabs.tsx` |
| **Planner (MD)** | `routes/PlannerMDNewPage.tsx` | `hooks/usePlannerStorage.ts`, `hooks/usePlannerConfig.ts` (version config), `components/deckBuilder/*` (Summary+Pane pattern), `components/startBuff/*` (Summary+EditPane pattern), `components/startGift/*` (Summary+EditPane pattern), `components/egoGift/EGOGiftObservation*` (Summary+EditPane pattern), `components/floorTheme/*`, `components/noteEditor/*` |
| **Planner List** | `routes/PlannerMDPage.tsx` (personal), `routes/PlannerMDGesellschaftPage.tsx` (community) | `hooks/useMDUserPlannersData.ts`, `hooks/useMDGesellschaftData.ts`, `hooks/useMDUserFilters.ts`, `hooks/useMDGesellschaftFilters.ts`, `types/MDPlannerListTypes.ts`, `components/plannerList/MDPlannerNavButtons.tsx`, `components/plannerList/MDPlannerToolbar.tsx` |
| **Extraction Calculator** | `routes/ExtractionPlannerPage.tsx`, `lib/extractionCalculator.ts` | `components/extraction/*`, `types/ExtractionTypes.ts` (featuredAnnouncerCount), `schemas/ExtractionSchemas.ts` |
| **Planner Sync** | `hooks/usePlannerSync.ts` | `hooks/usePlannerStorageAdapter.ts`, `hooks/usePlannerMigration.ts`, `lib/plannerApi.ts` |
| **Filter Sidebar** | `components/filter/FilterSidebar.tsx` | `FilterPageLayout.tsx`, `FilterSection.tsx`, `CompactIconFilter.tsx`, `SeasonDropdown.tsx`, `UnitKeywordDropdown.tsx`, `lib/filterUtils.ts` (calculateActiveFilterCount) |
| **Sanity Condition** | `lib/sanityConditionFormatter.ts` | `hooks/useSanityConditionData.ts` |
| **Authentication** | `routes/auth/callback/google.tsx` | `lib/api.ts`, `hooks/useAuthQuery.ts` |
| **Settings** | `routes/SettingsPage.tsx` | `components/settings/UsernameSection.tsx`, `hooks/useUserSettingsQuery.ts`, `schemas/UserSettingsSchemas.ts`, `types/UserSettingsTypes.ts` |
| **Notifications** | `components/notifications/NotificationDialog.tsx`, `components/notifications/NotificationIcon.tsx` | `hooks/useNotificationsQuery.ts`, `hooks/useUnreadCountQuery.ts`, `hooks/useMarkReadMutation.ts`, `hooks/useDeleteNotificationMutation.ts`, `schemas/NotificationSchemas.ts`, `types/NotificationTypes.ts` |
| **Moderation** | `routes/moderator/ModeratorDashboardPage.tsx` | `components/moderator/RecommendedPlannerList.tsx`, `components/moderator/HiddenPlannerList.tsx`, `hooks/useHideFromRecommendedMutation.ts`, `hooks/useUnhideFromRecommendedMutation.ts`, `hooks/useHiddenPlannersQuery.ts` |

### Backend Core Files

| Domain | Core Files | Supporting Files |
|--------|------------|------------------|
| **Authentication** | `controller/AuthController.java` | `service/JwtService.java`, `service/GoogleOAuthService.java`, `security/JwtAuthenticationFilter.java` |
| **User Management** | `service/UserService.java`, `controller/UserController.java` | `repository/UserRepository.java`, `entity/User.java`, `dto/user/UserDeletionResponse.java`, `dto/user/AssociationDto.java`, `dto/user/AssociationListResponse.java`, `dto/user/UpdateUsernameKeywordRequest.java` |
| **Username Generation** | `service/RandomUsernameGenerator.java`, `config/UsernameConfig.java` | `config/AssociationProvider.java`, `entity/User.java` (usernameKeyword, usernameSuffix) |
| **User Lifecycle** | `service/UserAccountLifecycleService.java` (deleteAccount, reactivateAccount, performHardDelete) | `scheduler/UserCleanupScheduler.java`, `exception/AccountDeletedException.java`, `facade/AuthenticationFacade.java` (reactivation) |
| **Planner CRUD** | `controller/PlannerController.java`, `service/PlannerService.java` | `repository/PlannerRepository.java`, `entity/Planner.java`, `entity/PlannerType.java`, `service/PlannerSseService.java`, `dto/planner/*` |
| **Planner Config** | `controller/PlannerController.java` (getConfig) | `dto/planner/PlannerConfigResponse.java`, `application.properties` (planner.schema-version, planner.md.current-version, planner.rr.available-versions) |
| **Planner Publishing** | `service/PlannerService.java` (togglePublish, castVote) | `entity/PlannerVote.java`, `entity/VoteType.java`, `repository/PlannerVoteRepository.java`, `dto/planner/PublicPlannerResponse.java`, `dto/planner/VoteRequest.java`, `converter/KeywordSetConverter.java` |
| **Planner View Tracking** | `service/PlannerService.java` (recordView) | `entity/PlannerView.java`, `entity/PlannerViewId.java`, `repository/PlannerViewRepository.java`, `util/ViewerHashUtil.java` |
| **Comment System** | `service/CommentService.java`, `controller/CommentController.java` | `entity/PlannerComment.java`, `entity/PlannerCommentVote.java`, `repository/PlannerCommentRepository.java`, `repository/PlannerCommentVoteRepository.java`, `dto/comment/*` |
| **Notification System** | `service/NotificationService.java`, `controller/NotificationController.java` | `entity/Notification.java`, `entity/NotificationType.java`, `repository/NotificationRepository.java`, `dto/planner/NotificationResponse.java`, `dto/planner/NotificationInboxResponse.java`, `dto/planner/UnreadCountResponse.java` |
| **Moderation System** | `service/ModerationService.java`, `controller/AdminModerationController.java` | `dto/planner/HidePlannerRequest.java`, `dto/planner/ModerationResponse.java`, `entity/Planner.java` (hiddenFromRecommended fields) |
| **Vote Immutability** | `entity/PlannerVote.java` (immutable voteType), `entity/PlannerCommentVote.java` (immutable voteType) | `exception/VoteAlreadyExistsException.java`, `service/PlannerService.java` (409 on re-vote), `service/CommentService.java` |
| **Configuration** | `config/SecurityConfig.java`, `config/WebConfig.java` | `config/CorsConfig.java`, `config/SecurityProperties.java`, `config/DeviceIdArgumentResolver.java`, `config/RateLimitConfig.java` |
| **Security Utilities** | `util/ClientIpResolver.java` | `config/SecurityProperties.java` (trusted proxy IPs) |
| **Exception Handling** | `exception/GlobalExceptionHandler.java` | `exception/PlannerNotFoundException.java`, `exception/PlannerConflictException.java`, `exception/PlannerForbiddenException.java`, `exception/PlannerValidationException.java`, `exception/UserNotFoundException.java`, `exception/AccountDeletedException.java`, `exception/RateLimitExceededException.java`, `exception/CommentNotFoundException.java`, `exception/CommentForbiddenException.java` |
| **Validation** | `validation/PlannerContentValidator.java`, `validation/ContentVersionValidator.java` | `validation/SinnerIdValidator.java`, `validation/GameDataRegistry.java` |

---

## Cross-Cutting Concerns

### Where Things Live

| Concern | Frontend Location | Backend Location |
|---------|-------------------|------------------|
| **Validation** | `schemas/*.ts` (Zod) | DTOs with Jakarta annotations |
| **i18n** | `lib/i18n.ts`, `static/i18n/{lang}/*.json` | N/A |
| **Username i18n** | `static/i18n/{lang}/association.json` | `config/UsernameConfig.java` (fallback) |
| **Theme** | `contexts/ThemeContext.tsx` | N/A |
| **Auth Tokens** | HttpOnly cookies (managed by backend) | `JwtService.java` |
| **API Client** | `lib/api.ts`, `lib/plannerApi.ts` | N/A |
| **Constants** | `lib/constants.ts` (EMPTY_STATE, SECTION_STYLES) | `application.properties` |
| **Asset Paths** | `lib/assetPaths.ts` | N/A |
| **Error Handling** | `components/common/ErrorBoundary.tsx` | `exception/GlobalExceptionHandler.java` |
| **Section Layout** | `components/common/PlannerSection.tsx` | N/A |
| **Card Grid Layout** | `components/common/ResponsiveCardGrid.tsx` | N/A |
| **Entity Sorting** | `lib/entitySort.ts` | N/A |
| **EGO Gift Filtering** | `lib/egoGiftFilter.ts` | N/A |
| **EGO Gift Selection** | `lib/egoGiftEncoding.ts` (encode/decode, cascade) | N/A |
| **Extraction Probability** | `lib/extractionCalculator.ts` (pity allocation, Coupon Collector model) | N/A |
| **Sanity Formatting** | `lib/sanityConditionFormatter.ts` | N/A |
| **Keyword Formatting** | `lib/keywordFormatter.ts`, `components/common/FormattedDescription.tsx` | N/A |
| **Search Mappings** | `hooks/useSearchMappings.ts` (deferred variant available) | N/A |
| **Filter Layout** | `components/filter/FilterSidebar.tsx`, `FilterPageLayout.tsx` | N/A |
| **Filter Utilities** | `lib/filterUtils.ts` (calculateActiveFilterCount) | IdentityPage, EGOPage (badge count calculation) |
| **Filter i18n** | `hooks/useFilterI18nData.ts` (returns seasonsI18n, unitKeywordsI18n) | `components/common/SeasonDropdown.tsx`, `UnitKeywordDropdown.tsx` (self-contained with internal fetch) |
| **Real-time Sync** | `hooks/usePlannerSync.ts` (SSE) | `service/PlannerSseService.java` |
| **Rate Limiting** | N/A | `config/RateLimitConfig.java` (Bucket4j) |
| **Client IP Resolution** | N/A | `util/ClientIpResolver.java` (trusted proxy validation) |
| **Security Headers** | N/A | `config/SecurityConfig.java` (HSTS, CSP, X-Frame-Options) |
| **CORS** | N/A | `config/CorsConfig.java` (explicit header whitelist) |
| **Cookie Security** | N/A | `util/CookieUtils.java` (SameSite=Lax, HttpOnly, Secure) |
| **Content Validation** | `schemas/PlannerSchemas.ts` | `validation/PlannerContentValidator.java` |
| **Version Validation** | `schemas/PlannerSchemas.ts` (PlannerConfigSchema) | `validation/ContentVersionValidator.java` (strict create, lenient update) |
| **Device Identification** | `lib/api.ts` (deviceId header) | `config/DeviceIdArgumentResolver.java` |
| **Privacy Hashing** | N/A | `util/ViewerHashUtil.java` (SHA-256) |

---

## Data Flow Patterns

### Frontend: Static JSON → Component

```
Static JSON Files (static/data/*.json)
         ↓
useSuspenseQuery (TanStack Query)
         ↓
Zod Schema Validation (schemas/*.ts)
         ↓
React Component
         ↓
Local State (useState) or IndexedDB (planner)
```

**Key Files:**
- Query setup: `lib/queryClient.ts`
- Validation utility: `lib/validation.ts`
- Example hook: `hooks/useIdentityListData.ts`

### Frontend: Deferred Hooks (Non-Blocking)

For list components that should remain visible during language changes, use `useQuery` instead of `useSuspenseQuery`:

```
useSuspenseQuery              useQuery (Deferred)
      │                             │
      ▼                             ▼
Triggers Suspense            Returns undefined/default
      │                             │
      ▼                             ▼
Component unmounts           Component stays mounted
until data loads             with fallback data
```

**Pattern:** Create paired hooks - a suspending version for initial load, and a deferred version for updates:
- `useSearchMappings()` → suspends, used in initial load
- `useSearchMappingsDeferred()` → returns empty Map while loading, used in list filtering
- `useEGOListI18nDeferred()` → returns empty object while loading, used for name search
- `useIdentityListI18nDeferred()` → returns empty object while loading, used for name search
- `useEGOGiftListI18nDeferred()` → returns empty object while loading, used for name search

**Where Used:**
- `EGOList.tsx`, `EGOGiftList.tsx`, `IdentityList.tsx` - use deferred hooks for search
- List remains visible during language switch, search results update after i18n loads

**Name Components with Internal Suspense:**
- `EGOName.tsx`, `IdentityName.tsx`, `EGOGiftName.tsx` - use suspending hooks internally
- Cards wrap Name components in their own Suspense boundary
- Enables per-card name loading without unmounting the grid

**Filter Dropdowns with Internal i18n:**
- `SeasonDropdown.tsx`, `UnitKeywordDropdown.tsx` - fetch i18n data internally via `useFilterI18nData()`
- Parent components wrap dropdowns in Suspense boundaries for loading states
- Self-contained pattern eliminates prop drilling of i18n data from pages

**Key Files:**
- `hooks/useSearchMappings.ts` (both variants)
- `hooks/useEGOListData.ts`, `hooks/useIdentityListData.ts`, `hooks/useEGOGiftListData.ts`
- `hooks/useFilterI18nData.ts` (seasons + unitKeywords i18n)
- `components/ego/EGOName.tsx`, `components/identity/IdentityName.tsx`, `components/egoGift/EGOGiftName.tsx`
- `components/common/SeasonDropdown.tsx`, `components/common/UnitKeywordDropdown.tsx`

### Backend: OAuth Flow

```
Frontend                    Backend                     Google
    │                          │                           │
    ├─[1] Initiate OAuth──────>│                           │
    │                          ├─[2] Redirect─────────────>│
    │<─[3] Auth Code───────────┤                           │
    ├─[4] POST /callback──────>│                           │
    │                          ├─[5] Exchange Code────────>│
    │                          │<─[6] Tokens───────────────┤
    │                          ├─[7] Get UserInfo─────────>│
    │                          │<─[8] User Data────────────┤
    │                          ├─[8.5] Generate Username───┤ (new users only)
    │<─[9] Set Cookies─────────┤                           │
```

**Username Generation (step 8.5, new users only):**
- Format: `Faust-{Association}-{5-char suffix}`
- Association: Weighted random from `UsernameConfig` (time-decay: 3/2/1 for 0-30/31-60/61+ days)
- Suffix: SecureRandom, 31-char safe alphanumeric set (excludes 0,1,O,I,L)
- Collision: DB UNIQUE constraint + retry loop

**Key Files:**
- `controller/AuthController.java` (steps 4, 9)
- `service/GoogleOAuthService.java` (steps 5-8)
- `service/JwtService.java` (token generation)
- `service/RandomUsernameGenerator.java` (step 8.5)
- `config/UsernameConfig.java` (association list + weights)

### Planner Sync Flow

```
Frontend                      Backend                      Database
    │                            │                            │
    ├─[1] Load Local────────────>│                            │
    │    (IndexedDB)             │                            │
    │                            │                            │
    ├─[2] GET /planners─────────>│                            │
    │    (if authenticated)      ├─[3] Query──────────────────>│
    │                            │<─[4] Planners───────────────┤
    │<─[5] Server Planners───────┤                            │
    │                            │                            │
    ├─[6] Merge (lastModified)──>│                            │
    │                            │                            │
    ├─[7] PUT /planners/{id}────>│                            │
    │    (on local change)       ├─[8] Upsert─────────────────>│
    │                            │<─[9] Updated────────────────┤
    │<─[10] Confirm──────────────┤                            │
    │                            │                            │
    │<─[11] SSE: planner_updated─┤ (other devices)            │
    │                            │                            │
```

**Key Files:**
- `hooks/usePlannerSync.ts` (sync orchestration)
- `hooks/usePlannerStorageAdapter.ts` (local/remote abstraction)
- `lib/plannerApi.ts` (API calls)
- `controller/PlannerController.java` (REST + SSE endpoints)
- `service/PlannerService.java` (conflict resolution)
- `service/PlannerSseService.java` (real-time notifications)

### Planner Publishing & Voting Flow

```
Frontend                      Backend                      Database
    │                            │                            │
    ├─[1] PUT /{id}/publish─────>│                            │
    │    (owner only)            ├─[2] Check ownership────────>│
    │                            │<─[3] Planner────────────────┤
    │                            ├─[4] Toggle published────────>│
    │<─[5] Updated planner───────┤                            │
    │                            │                            │
    ├─[6] GET /published────────>│ (permitAll)                │
    │                            ├─[7] Query published─────────>│
    │                            │<─[8] Page<Planner>──────────┤
    │<─[9] PublicPlannerResponse─┤                            │
    │                            │                            │
    ├─[10] POST /{id}/vote──────>│                            │
    │     {voteType: UP/DOWN}    ├─[11] Rate limit check       │
    │                            ├─[12] Upsert PlannerVote────>│
    │                            ├─[13] Update vote counts────>│
    │<─[14] VoteResponse─────────┤                            │
```

**Key Files:**
- `controller/PlannerController.java` (publish/vote endpoints)
- `service/PlannerService.java` (togglePublish, castVote, getPublished, getRecommended)
- `repository/PlannerVoteRepository.java` (vote persistence)
- `entity/PlannerVote.java` + `PlannerVoteId.java` (composite key)
- `config/RateLimitConfig.java` (Bucket4j rate limiting)

**Public Endpoints (no auth required):**
- `GET /api/planner/md/config` - get planner version config (schemaVersion, mdCurrentVersion, rrAvailableVersions)
- `GET /api/planner/md/published` - browse all published planners
- `GET /api/planner/md/recommended` - planners with net votes >= threshold
- `POST /api/planner/md/{id}/view` - record view (daily deduplication, 204 response)
- `GET /api/user/associations` - list 11 faction keywords for settings page
- `GET /api/planner/{id}/comments` - list comments on published planner

### Comment System Flow

```
Frontend                      Backend                      Database
    │                            │                            │
    ├─[1] GET /{id}/comments───>│ (permitAll for published)  │
    │                            ├─[2] Query comments─────────>│
    │                            │<─[3] Flat list──────────────┤
    │                            ├─[4] Batch load users────────>│
    │                            │<─[5] User map───────────────┤
    │<─[6] CommentResponse[]────┤ (with author, upvoteCount)  │
    │                            │                            │
    ├─[7] POST /{id}/comments──>│ (auth required)            │
    │     {content, parentId?}   ├─[8] Rate limit check       │
    │                            ├─[9] Depth calculation───────>│ (max 5, flatten)
    │                            ├─[10] Insert comment─────────>│
    │                            ├─[11] Create notification───>│ (COMMENT_RECEIVED or REPLY_RECEIVED)
    │<─[12] CommentResponse─────┤                            │
    │                            │                            │
    ├─[13] POST /comments/{id}/upvote───────────────────────>│
    │                            ├─[14] Immutable vote logic   │
    │                            │     (create only, 409 on duplicate)
    │                            ├─[15] Atomic counter─────────>│
    │<─[16] CommentVoteResponse─┤ (upvoteCount, hasUpvoted)  │
```

**Key Files:**
- `controller/CommentController.java` (CRUD + vote endpoints)
- `service/CommentService.java` (threading, voting, atomic counters, notification integration)
- `entity/PlannerComment.java` (parentCommentId, depth, upvoteCount)
- `entity/PlannerCommentVote.java` (composite key, immutable voteType)
- `repository/PlannerCommentRepository.java` (incrementUpvoteCount, decrementUpvoteCount)
- `config/RateLimitConfig.java` (comment bucket: 10 ops/min)

**Threading Logic:**
- `depth = 0`: Top-level comment
- `depth = 1-4`: Normal replies (parent.depth + 1)
- `depth = 5`: Flattened (becomes sibling of parent, uses parent's parentId)

**Vote Immutability (V018 Migration):**
- **BREAKING**: Votes are immutable - can only be created, never updated or deleted
- No vote → Create new (increment counter)
- Vote exists → 409 Conflict (`VoteAlreadyExistsException`)
- Removed: `deletedAt`, `updatedAt`, `version` fields from vote entities

**User Deletion Integration:**
- Comments: Reassigned to sentinel user (id=0)
- Votes: Reassigned to sentinel user (id=0) via `reassignUserVotes()`

### Notification System Flow

```
Frontend                      Backend                      Database
    │                            │                            │
    ├─[1] GET /notifications/inbox───────────────────────────>│
    │                            ├─[2] Query notifications────>│
    │                            │    (user_id, !deleted, page)
    │<─[3] NotificationInboxResp─┤                            │
    │                            │                            │
    ├─[4] GET /unread-count────>│ (30-second polling)        │
    │                            ├─[5] Count unread───────────>│
    │<─[6] UnreadCountResponse───┤                            │
    │                            │                            │
    │                            │  [Vote threshold crossed]  │
    │                            ├─[7] trySetRecommendedNotified() (atomic)
    │                            ├─[8] Create notification───>│ (PLANNER_RECOMMENDED)
    │                            │                            │
    │                            │  [Comment received]        │
    │                            ├─[9] Create notification───>│ (COMMENT_RECEIVED)
    │                            │                            │
    │                            │  [Reply received]          │
    │                            ├─[10] Create notification──>│ (REPLY_RECEIVED)
    │                            │                            │
    ├─[11] POST /{id}/mark-read>│                            │
    │                            ├─[12] Update read flag──────>│
    │<─[13] NotificationResponse─┤                            │
    │                            │                            │
    ├─[14] DELETE /{id}────────>│                            │
    │                            ├─[15] Soft-delete notification─>│
    │<─[16] 204 No Content───────┤                            │
```

**Key Files:**
- `controller/NotificationController.java` (inbox, unread count, mark read, delete)
- `service/NotificationService.java` (creation, deduplication, cleanup scheduler)
- `entity/Notification.java` (user_id, content_id, type, read, deleted_at)
- `entity/NotificationType.java` (PLANNER_RECOMMENDED, COMMENT_RECEIVED, REPLY_RECEIVED, REPORT_RECEIVED)
- `repository/NotificationRepository.java` (atomic queries, UNIQUE constraint)
- `repository/PlannerRepository.java` (trySetRecommendedNotified for race condition prevention)

**Atomic Notification Pattern:**
- Threshold detection: net votes 9 → 10 triggers notification
- Race condition mitigation: `trySetRecommendedNotified()` uses `WHERE ... AND recommendedNotifiedAt IS NULL`
- Returns affected row count (1 = success, 0 = already notified)
- UNIQUE constraint on (user_id, content_id, notification_type) prevents duplicate notifications

**Deduplication:**
- UNIQUE constraint enforced at database level
- `createNotification()` uses `INSERT IGNORE` pattern
- Prevents spam from concurrent comment/vote actions

**Cleanup:**
- Scheduled job: soft-deleted notifications older than 90 days → hard delete
- Read notifications older than 90 days → hard delete
- Runs daily via `@Scheduled` annotation

### Moderation System Flow

```
Frontend                      Backend                      Database
    │                            │                            │
    ├─[1] GET /gesellschaft────>│ (recommended filter)       │
    │                            ├─[2] Query───────────────────>│
    │                            │    WHERE hiddenFromRecommended = false
    │<─[3] PublicPlannerPage────┤                            │
    │                            │                            │
    ├─[4] POST /admin/planner/{id}/hide────────────────────>│
    │     {reason: string}       ├─[5] Check ROLE_ADMIN      │
    │                            ├─[6] Update planner─────────>│
    │                            │    SET hiddenFromRecommended = true
    │<─[7] ModerationResponse───┤                            │
    │                            │                            │
    ├─[8] POST /admin/planner/{id}/unhide──────────────────>│
    │                            ├─[9] Check ROLE_ADMIN      │
    │                            ├─[10] Update planner────────>│
    │                            │    SET hiddenFromRecommended = false
    │<─[11] ModerationResponse───┤                            │
```

**Key Files:**
- `controller/AdminModerationController.java` (hide/unhide endpoints, @PreAuthorize)
- `service/ModerationService.java` (manual curation logic)
- `entity/Planner.java` (hiddenFromRecommended, hiddenByModeratorId, hiddenReason, hiddenAt)
- `repository/PlannerRepository.java` (findRecommendedPlanners filters hidden planners)

**Manual Curation Pattern (arca.live):**
- Moderators can hide planners from recommended list WITHOUT deleting votes
- Hidden planners retain vote counts (allows unhide without data loss)
- Recommended query filters `hiddenFromRecommended = false`
- Hide reason required (10-500 chars, stored for audit trail)
- Moderator ID tracked for accountability

**Authorization:**
- Endpoints require `ROLE_ADMIN` OR `ROLE_MODERATOR`
- Frontend moderator dashboard accessible only to authorized users
- Backend enforced via `@PreAuthorize` annotation

---

## Feature Domain Deep Dives

### Identity/EGO/Gift Browser Pattern

All three browse features follow the same pattern:

```
┌─────────────────────────────────────────────────────────┐
│ ListPage (e.g., IdentityPage.tsx)                       │
│   ├── FilterPageLayout                                  │
│   │     ├── FilterSidebar (desktop: sticky, mobile: expandable) │
│   │     │     ├── FilterSection (collapsible groups)   │
│   │     │     └── CompactIconFilter, Dropdowns         │
│   │     └── Content Area                               │
│   ├── SearchBar                                         │
│   └── List Component (IdentityList)                     │
│         ├── sortByReleaseDate() (updateDate DESC)       │
│         └── ResponsiveCardGrid                          │
│               └── Card Components (IdentityCardLink)    │
└─────────────────────────────────────────────────────────┘
                          │
                          │ onClick
                          ▼
┌─────────────────────────────────────────────────────────┐
│ DetailPage (e.g., IdentityDetailPage.tsx)               │
│   ├── Header (name, image)                              │
│   └── Panels (stats, skills, resistances)              │
└─────────────────────────────────────────────────────────┘
```

**Card Grid Layout:**
- `ResponsiveCardGrid`: CSS Grid auto-fill with centered alignment
- Card widths defined in `CARD_GRID` constant (Identity: 160px, EGO: 160px, EGO Gift: 96px)
- Dynamic column count based on container width

**Card Component Pattern:**
- `IdentityCard`, `EGOCard`: Pure view-only components with `overlay` prop for custom content
- Both cards have Layer 5 info panel: level display + Suspense-wrapped name component
- `overlay` prop enables composition (selected indicators, deployment badges) without modifying core card
- `SinnerDeckCard` reuses `IdentityCard` with deployment overlay instead of duplicating render logic
- Callers control overlay content - cards don't manage selection state internally
- Cards wrap `*Name` components in Suspense boundaries for fine loading during language switch

**Sorting:**
- `sortByReleaseDate()` in `lib/entitySort.ts`: updateDate DESC → rank DESC → id DESC (Identity)
- `sortEGOByDate()` in `lib/entitySort.ts`: updateDate DESC → egoType tier DESC → sinner DESC → id DESC (EGO)
- EGO type tier order: ALEPH > WAW > HE > TETH > ZAYIN
- Data corrections for raw game data errors: `scripts/ego.py` → `DATA_CORRECTIONS` dict

**Pattern Files to Reference:**
- List page: `routes/IdentityPage.tsx`
- Detail page: `routes/IdentityDetailPage.tsx` (fine Suspense pattern)
- List data hook: `hooks/useIdentityListData.ts`
- Detail data hook: `hooks/useIdentityDetailData.ts` (paired spec/i18n hooks)
- I18n components: `PassiveI18n.tsx`, `SkillI18n.tsx`, `SanityI18n.tsx`, `StyledName.tsx`
- Card components: `components/identity/IdentityCard.tsx`, `components/ego/EGOCard.tsx` (overlay + Layer 5 pattern)
- Name components: `components/identity/IdentityName.tsx`, `components/ego/EGOName.tsx` (Suspense-wrapped i18n)
- Card grid: `components/common/ResponsiveCardGrid.tsx`
- Sort utility: `lib/entitySort.ts`
- Filter utilities: `lib/filterUtils.ts` (calculateActiveFilterCount for badge counts), `lib/egoGiftFilter.ts` (EGO Gift-specific tier/difficulty/filter logic)
- Filter layout: `components/filter/FilterPageLayout.tsx`, `FilterSidebar.tsx`
- Sanity formatter: `lib/sanityConditionFormatter.ts`
- Keyword formatter: `lib/keywordFormatter.ts`, `components/common/FormattedDescription.tsx`

### Modular Detail Page Layout Pattern

Reusable layout system for entity detail pages (Identity, EGO, EGO Gift):

```
┌───────────────────────────────────────────────────────────────────┐
│ DetailPageLayout                                                   │
│   ├── DetailLeftPanel (4:6 ratio)                                 │
│   │     └── Entity image, basic info                              │
│   │                                                                │
│   └── DetailRightPanel (4:6 ratio)                                │
│         ├── DetailEntitySelector (sticky)                         │
│         │     └── Tier/Level sliders (Uptie, Skill Level, etc.)  │
│         └── ScrollArea (content panels)                           │
│               └── Skills, Passives, Stats, etc.                   │
│                                                                    │
│   Mobile: MobileDetailTabs (Skills | Passives | Sanity tabs)      │
└───────────────────────────────────────────────────────────────────┘
```

**Key Components:**
- `DetailPageLayout`: Main container with responsive 4:6 column ratio
- `DetailEntitySelector`: Uptie/level slider controls with visual indicators
- `DetailLeftPanel`: Entity portrait and summary info
- `DetailRightPanel`: Scrollable content with sticky selector
- `MobileDetailTabs`: Tab navigation for mobile view

**Constants (lib/constants.ts):**
- `DETAIL_PAGE.LEFT_PANEL_RATIO`, `DETAIL_PAGE.RIGHT_PANEL_RATIO`
- `SANITY_INDICATOR_COLORS.POSITIVE`, `SANITY_INDICATOR_COLORS.NEGATIVE`
- `CURRENT_MD_VERSION`, `MD_ACCENT_COLORS` (Mirror Dungeon version theming)

**Pattern Files to Reference:**
- Layout: `components/common/DetailPageLayout.tsx`
- Selector: `components/common/DetailEntitySelector.tsx`
- Implementation: `routes/IdentityDetailPage.tsx`, `routes/EGOGiftDetailPage.tsx` (click-to-reveal variant)

### Settings Page Pattern (Public with Gated Content)

The settings page demonstrates public access with authenticated-only sections:

```
┌─────────────────────────────────────────────────────────┐
│ SettingsPage (public access)                             │
│   └── UsernameSection                                    │
│         ├── Unauthenticated: Sign-in prompt + OAuth btn │
│         └── Authenticated: Dropdown + Preview + Save    │
│               ├── useAssociationsQuery (public GET)     │
│               ├── useUpdateKeywordMutation (auth PUT)   │
│               └── Cache invalidation → Header refresh   │
└─────────────────────────────────────────────────────────┘
```

**Key Pattern:**
- Page loads for all users (no redirect)
- Content gated by `useAuthQuery()` check
- OAuth flow reused from Header (tech debt: consider `useGoogleLogin` hook)
- Live preview with local state before server commit

**Pattern Files to Reference:**
- Page: `routes/SettingsPage.tsx`
- Section: `components/settings/UsernameSection.tsx`
- Data hooks: `hooks/useUserSettingsQuery.ts`
- Backend: `controller/UserController.java` (GET/PUT endpoints)

### Planner Feature (Complex)

The planner page (`PlannerMDNewPage.tsx`) is the most complex, with multiple sections:

```
┌─────────────────────────────────────────────────────────┐
│ PlannerMDNewPage (~720 lines)                           │
│   │                                                     │
│   │  All sections wrapped in <PlannerSection>           │
│   │  (unified h2 header + bordered container)           │
│   │                                                     │
│   ├── DeckBuilder Section (Summary + Pane)              │
│   │     ├── Summary: SinnerGrid, StatusViewer, ActionBar│
│   │     └── Pane: Filters, EntityToggle, TierSelector  │
│   ├── StartBuff Section (Summary + EditPane)            │
│   │     ├── Summary: StartBuffMiniCard (selected only) │
│   │     └── EditPane: StartBuffCard, EnhancementButton │
│   ├── StartGift Section (Summary + EditPane)            │
│   │     ├── Summary: StartGiftSummary (selected only)  │
│   │     └── EditPane: StartGiftEditPane, StartGiftRow  │
│   ├── EGO Gift Observation Section (Summary + EditPane) │
│   │     ├── Summary: EGOGiftObservationSummary         │
│   │     └── EditPane: EGOGiftObservationEditPane       │
│   ├── Comprehensive EGO Gift Section (Summary + Pane)   │
│   │     ├── Summary: ComprehensiveGiftSummary          │
│   │     ├── Pane: ComprehensiveGiftSelectorPane        │
│   │     └── getCascadeIngredients (recipe → ingredients)│
│   ├── Skill Replacement Section                         │
│   │     └── SkillExchangePane, SkillEADisplay          │
│   ├── Floor Theme Sections (×15)                        │
│   │     └── FloorGiftSelectorPane                      │
│   └── Note Editors (×N)                                 │
│         └── NoteEditor with Tiptap                     │
└─────────────────────────────────────────────────────────┘
```

**Section Wrapper Pattern:**
- `PlannerSection`: Unified wrapper providing consistent h2 + container styling
- Uses `SECTION_STYLES` tokens from `lib/constants.ts`
- `SectionContainer`: Deprecated, use PlannerSection instead

**Summary + Pane UI Pattern:**
- Summary components use native `<button type="button">` (not div role="button")
- Empty states use `EMPTY_STATE.MIN_HEIGHT` + `EMPTY_STATE.DASHED_BORDER` constants
- EditPane dialogs have `Reset | Done` layout in DialogFooter (Reset: outline/left, Done: default/right)
- Filter state resets on dialog close via useEffect with `[open]` dependency

**State Management:**
- ~15 useState hooks for different sections
- Lifted filter state: `DeckFilterState` for DeckBuilder pane persistence
- Auto-save: `hooks/usePlannerAutosave.ts` (2-second debounce)
- Persistence: `hooks/usePlannerStorage.ts` (IndexedDB)

**Planner Versioning:**
- `schemaVersion`: Data format version (for migration support)
- `contentVersion`: Game content version (MD6, RR5, etc.)
- `plannerType`: MIRROR_DUNGEON or REFRACTED_RAILWAY
- Version config fetched via `usePlannerConfig.ts` hook
- Backend config: `application.properties` (planner.schema-version, planner.md.current-version, planner.rr.available-versions)

**Planner Discriminated Union Pattern:**

The planner uses a discriminated union pattern to support multiple planner types (Mirror Dungeon, Refracted Railway) with type-safe category validation:

```typescript
// Config layer (new) - discriminated by 'type' field
interface MDConfig { type: 'MIRROR_DUNGEON'; category: MDCategory }
interface RRConfig { type: 'REFRACTED_RAILWAY'; category: RRCategory }
type PlannerConfig = MDConfig | RRConfig

// SaveablePlanner structure
interface SaveablePlanner {
  metadata: PlannerMetadata    // id, status, timestamps, syncVersion
  config: PlannerConfig        // discriminated union for type + category
  content: MDPlannerContent | RRPlannerContent  // type-specific content
}
```

**Why Config Layer:**
- Category moved from `content` to `config` for lightweight summaries (list views don't need full content)
- Discriminated union enables TypeScript narrowing: `if (config.type === 'MIRROR_DUNGEON')` narrows `config.category` to `MDCategory`
- Single source of truth for planner type (eliminates redundancy with `metadata.plannerType`)

**Two-Step Validation (Zod limitation):**
Zod's `z.discriminatedUnion` cannot validate cross-object relationships (config.type vs content type). Solution:
1. Step 1: Validate config structure with `PlannerConfigDiscriminatedSchema`
2. Step 2: Validate content matches config.type in `validateSaveablePlanner()`

**Key Files:**
- Types: `types/PlannerTypes.ts` (MDConfig, RRConfig, PlannerConfig, SaveablePlanner)
- Schema: `schemas/PlannerSchemas.ts` (PlannerConfigDiscriminatedSchema, validateSaveablePlanner)
- Constants: `lib/constants.ts` (MD_CATEGORIES, RR_CATEGORIES)
- Backend: `entity/MDCategory.java`, `entity/RRCategory.java` (isValid helpers)
- Service: `service/PlannerService.java` (isValidCategory for type-based validation)

**Key Data Hooks:**
- `usePlannerConfig.ts` (version config)
- `useStartBuffData.ts`
- `useStartGiftPools.ts`
- `useEGOGiftObservationData.ts`
- `useEGOGiftListData.ts`
- `useThemePackListData.ts`

**Summary + Pane Pattern Components:**
- StartBuff: `StartBuffSection.tsx`, `StartBuffEditPane.tsx`
- StartGift: `StartGiftSummary.tsx`, `StartGiftEditPane.tsx`
- EGO Gift Observation: `EGOGiftObservationSummary.tsx`, `EGOGiftObservationEditPane.tsx`
- Comprehensive Gift: `ComprehensiveGiftSummary.tsx`, `ComprehensiveGiftSelectorPane.tsx` (with cascade selection)

### EGO Gift Recipe & Cascade Selection

Gifts with recipes auto-select their ingredients when chosen in the Comprehensive Gift section:

```
User selects gift 9088 (has recipe)
         ↓
handleEnhancementSelect(giftId, enhancement)
         ↓
getCascadeIngredients(recipe) → [9003, 9053, 9101, 9155, 9157]
         ↓
Add all ingredients to selection (enhancement=0)
```

**Recipe Types (types/EGOGiftTypes.ts):**

| Type | Structure | Cascade Behavior |
|------|-----------|------------------|
| **Standard** | `{ materials: [[id1, id2], [id1, id3]] }` | Union all unique IDs across options |
| **Mixed** (Lunar Memory) | `{ type: 'mixed', a: {ids, count}, b: {ids, count} }` | Skip cascade (manual selection) |

**Key Files:**
- Types: `types/EGOGiftTypes.ts` (StandardRecipe, MixedRecipe, EGOGiftRecipe)
- Schema: `schemas/EGOGiftSchemas.ts` (EGOGiftRecipeSchema)
- Utility: `lib/egoGiftEncoding.ts` (getCascadeIngredients)
- Component: `components/egoGift/ComprehensiveGiftSelectorPane.tsx` (cascade in handleEnhancementSelect)
- Tests: `lib/__tests__/egoGiftEncoding.test.ts` (32 tests)

### Planner List Pattern (Route-Based Separation)

Personal and community planners are served by separate routes with independent data sources:

```
/planner/md                    /planner/md/gesellschaft
    │                               │
    ▼                               ▼
PlannerMDPage.tsx              PlannerMDGesellschaftPage.tsx
    │                               │
    ├── useMDUserFilters           ├── useMDGesellschaftFilters
    │   (URL: category, page, q)   │   (URL: category, page, mode, q)
    │                               │
    ├── useMDUserPlannersData      ├── useMDGesellschaftData
    │   (IndexedDB guest / API)    │   (published/recommended API)
    │                               │
    └── MDPlannerNavButtons        └── MDPlannerToolbar
        (active route detection)       (search + mode toggle)
```

**URL State Pattern:**
- Zod schemas validate URL params with `.max(200)` on search
- Default values (`page=0`, `mode='published'`) hidden from URL
- Filter hooks expose `search` + `setFilters({ q, page: 0 })`

**Data Source Separation:**
- Personal: `usePlannerStorageAdapter` → IndexedDB (guest) or API (auth)
- Community: Direct API calls to `/api/planner/md/published` or `/recommended`
- Query keys namespaced (`'userPlanners'` vs `'gesellschaft'`) to prevent cache collision

**Key Files:**
- Types: `types/MDPlannerListTypes.ts`
- Personal hooks: `hooks/useMDUserPlannersData.ts`, `hooks/useMDUserFilters.ts`
- Community hooks: `hooks/useMDGesellschaftData.ts`, `hooks/useMDGesellschaftFilters.ts`
- Components: `components/plannerList/MDPlannerNavButtons.tsx`, `MDPlannerToolbar.tsx`
- Utility: `lib/constants.ts` (`calculatePlannerPages`)

---

## File Dependency Graph

### Frontend Dependencies

```
main.tsx
    └── lib/router.tsx
          ├── routes/PlannerMDPage.tsx (personal planners)
          │     ├── hooks/useMDUserPlannersData.ts (IndexedDB + server merge)
          │     ├── hooks/useMDUserFilters.ts (URL state: category, page, q)
          │     └── components/plannerList/MDPlannerNavButtons.tsx
          ├── routes/PlannerMDGesellschaftPage.tsx (community planners)
          │     ├── hooks/useMDGesellschaftData.ts (published/recommended API)
          │     ├── hooks/useMDGesellschaftFilters.ts (URL state: category, page, mode, q)
          │     └── components/plannerList/MDPlannerToolbar.tsx
          └── routes/*Page.tsx
                ├── hooks/use*Data.ts
                │     ├── schemas/*Schemas.ts
                │     └── lib/validation.ts
                ├── components/filter/FilterPageLayout.tsx
                │     ├── FilterSidebar.tsx
                │     └── FilterSection.tsx
                ├── components/{domain}/*List.tsx
                │     ├── lib/entitySort.ts (sortByReleaseDate, sortEGOByDate)
                │     └── components/common/ResponsiveCardGrid.tsx
                │           └── lib/constants.ts (CARD_GRID)
                ├── components/{domain}/*.tsx
                │     └── lib/assetPaths.ts
                ├── lib/sanityConditionFormatter.ts
                │     └── hooks/useSanityConditionData.ts
                ├── components/common/DetailPageLayout.tsx
                │     ├── DetailLeftPanel.tsx
                │     ├── DetailRightPanel.tsx (uses ui/scroll-area)
                │     ├── DetailEntitySelector.tsx (uses ui/slider)
                │     └── MobileDetailTabs.tsx
                ├── components/common/PlannerSection.tsx (planner pages)
                │     └── lib/constants.ts (SECTION_STYLES)
                ├── hooks/usePlannerConfig.ts (planner version config)
                │     └── schemas/PlannerSchemas.ts (PlannerConfigSchema)
                └── lib/constants.ts (DETAIL_PAGE, SANITY_INDICATOR_COLORS, PLANNER_TYPES)
```

### Backend Dependencies

```
BackendApplication.java
    ├── config/SecurityConfig.java (HSTS, CSP, X-Frame-Options)
    │     └── security/JwtAuthenticationFilter.java
    │           └── service/JwtService.java
    ├── config/SecurityProperties.java (trusted proxy IPs)
    │     └── util/ClientIpResolver.java (X-Forwarded-For validation)
    └── config/WebConfig.java
          └── config/DeviceIdArgumentResolver.java

controller/AuthController.java
    ├── util/ClientIpResolver.java (rate limit IP)
    ├── config/SecurityProperties.java (trusted proxies)
    ├── service/UserService.java
    │     └── repository/UserRepository.java
    ├── service/JwtService.java
    └── service/GoogleOAuthService.java

controller/PlannerController.java
    ├── util/ClientIpResolver.java (view tracking IP)
    ├── config/SecurityProperties.java (trusted proxies)
    ├── config/RateLimitConfig.java (Bucket4j rate limiting)
    ├── dto/planner/PlannerConfigResponse.java (version config)
    ├── entity/PlannerType.java (MIRROR_DUNGEON, REFRACTED_RAILWAY)
    ├── service/PlannerService.java (configurable via @Value)
    │     ├── repository/PlannerRepository.java
    │     │     ├── entity/Planner.java
    │     │     └── Atomic methods (incrementUpvotes, decrementUpvotes, incrementViewCount)
    │     ├── repository/PlannerVoteRepository.java
    │     │     └── entity/PlannerVote.java (@IdClass: PlannerVoteId)
    │     │           └── entity/VoteType.java (enum: UP only - upvote-only system)
    │     ├── repository/PlannerViewRepository.java
    │     │     └── entity/PlannerView.java (@IdClass: PlannerViewId)
    │     ├── util/ViewerHashUtil.java (SHA-256 privacy hashing)
    │     ├── validation/ContentVersionValidator.java (@Value version config)
    │     ├── validation/PlannerContentValidator.java (@Value size limits)
    │     │     ├── validation/GameDataRegistry.java
    │     │     └── validation/SinnerIdValidator.java
    │     └── converter/KeywordSetConverter.java (MySQL SET)
    └── service/PlannerSseService.java (SSE + zombie cleanup, DEBUG logs)

controller/CommentController.java
    ├── config/RateLimitConfig.java (comment bucket: 10 ops/min)
    └── service/CommentService.java
          ├── repository/PlannerCommentRepository.java
          │     ├── entity/PlannerComment.java (threading: parentCommentId, depth, upvoteCount)
          │     └── Atomic methods (incrementUpvoteCount, decrementUpvoteCount)
          ├── repository/PlannerCommentVoteRepository.java
          │     └── entity/PlannerCommentVote.java (@IdClass: PlannerCommentVoteId, Persistable)
          │           └── entity/CommentVoteType.java (enum: UP)
          ├── repository/PlannerRepository.java (verify planner exists/published)
          └── repository/UserRepository.java (batch load authors)

exception/GlobalExceptionHandler.java (hybrid error handling)
    └── exception/*Exception.java (Planner*, User*, RateLimit*, Comment*)

dto/planner/PublicPlannerResponse.java (shows authorUsernameKeyword + Suffix)
```

---

## When Modifying: Impact Analysis

### High-Impact Files (Modify with Care)

| File | Impact | What Depends On It |
|------|--------|-------------------|
| `lib/constants.ts` | High | All components using constants |
| `lib/queryClient.ts` | High | All data fetching |
| `lib/router.tsx` | High | All navigation |
| `lib/plannerApi.ts` | High | All planner sync operations |
| `schemas/index.ts` | Medium | All validation |
| `config/SecurityConfig.java` | High | All authenticated requests |
| `service/JwtService.java` | High | All auth flows |
| `service/PlannerService.java` | High | All planner CRUD and sync, notification integration |
| `service/CommentService.java` | Medium | All comment CRUD and voting, notification integration |
| `service/NotificationService.java` | Medium | All notification features, planner/comment services |
| `service/ModerationService.java` | Low | Admin moderation features only |
| `config/RateLimitConfig.java` | High | All rate-limited endpoints |
| `validation/PlannerContentValidator.java` | High | All planner create/update |
| `validation/ContentVersionValidator.java` | High | Planner create/import (version enforcement) |
| `exception/GlobalExceptionHandler.java` | High | All error responses |
| `util/ClientIpResolver.java` | High | All rate-limited endpoints |
| `config/SecurityProperties.java` | High | ClientIpResolver, rate limiting |
| `util/CookieUtils.java` | High | All auth cookie operations |

### Safe to Modify (Isolated)

| File | Impact | Notes |
|------|--------|-------|
| Individual `components/{domain}/*.tsx` | Low | Domain-isolated |
| Individual `routes/*Page.tsx` | Low | Page-isolated |
| Individual DTOs | Low | Single endpoint |

### Medium-Impact (Shared Components)

| File | Impact | What Uses It |
|------|--------|-------------|
| `components/common/DetailPageLayout.tsx` | Medium | All detail pages (Identity, EGO, EGO Gift) |
| `components/common/DetailEntitySelector.tsx` | Medium | DetailRightPanel, all entity detail pages |
| `components/common/PlannerSection.tsx` | Medium | All planner sections (DeckBuilder, StartBuff, StartGift, EGOGift*, SkillReplacement, FloorThemes) |
| `components/common/ResponsiveCardGrid.tsx` | Medium | IdentityList, EGOList, EGOGiftList, EGOGiftSelectionList |
| `lib/entitySort.ts` | Low | IdentityList, EGOList |
| `lib/sanityConditionFormatter.ts` | Low | IdentityDetailPage |
| `components/filter/FilterSidebar.tsx` | Medium | IdentityPage, EGOPage, EGOGiftPage |
| `components/filter/FilterPageLayout.tsx` | Medium | All list pages |
| `components/Header.tsx` | High | All pages (global layout), notification icon |
| `dto/planner/PublicPlannerResponse.java` | Medium | All public planner endpoints |

### Breaking Changes (V018-V021 Migrations)

**Vote Immutability (BREAKING):**
- **Before**: Votes could be toggled (UP ↔ null ↔ DOWN)
- **After**: Votes are immutable - can only be created once
- **API Change**: `POST /api/planner/{id}/vote` with `voteType: null` returns 400 Bad Request
- **Error Response**: Duplicate vote attempts return 409 Conflict with `VoteAlreadyExistsException`
- **Frontend Impact**: Vote buttons disable after voting, no toggle UI
- **Database**: Removed `deleted_at`, `updated_at`, `version` from vote tables

**Vote API Contract:**
```typescript
// Before (allowed null for removal)
interface VoteRequest {
  voteType: 'UP' | 'DOWN' | null;
}

// After (immutable voting)
interface VoteRequest {
  voteType: 'UP' | 'DOWN'; // null rejected with 400
}
```

**Frontend Migration Checklist:**
- `usePlannerVote.ts`: Updated type signature, removed toggle logic
- `PlannerCardContextMenu.tsx`: Disabled buttons after vote, removed toggle handlers
- i18n: Removed `removeUpvote`/`removeDownvote`, added `upvoted`/`downvoted`/`alreadyVoted`
- Pre-vote warning modal: Added localStorage-based warning for permanent vote commitment

**Notification System (New):**
- Real-time notifications via Header bell icon
- 30-second polling for unread count
- Notification types: PLANNER_RECOMMENDED, COMMENT_RECEIVED, REPLY_RECEIVED, REPORT_RECEIVED
- Atomic threshold detection prevents duplicate notifications
- Frontend components: NotificationDialog, NotificationIcon, NotificationItem

**Moderation System (New):**
- Admin dashboard at `/moderator/dashboard`
- Manual curation: hide planners from recommended list without deleting votes
- Authorization: `ROLE_ADMIN` OR `ROLE_MODERATOR` required
- Frontend components: RecommendedPlannerList, HiddenPlannerList, HideReasonModal

---

## Static Data Sources

### Frontend (static/data/)

| Path Pattern | Contents | Loaded By |
|--------------|----------|-----------|
| `identity{id}.json` | Identity details | `useIdentityDetailData` |
| `identitySpecList.json` | All identity specs | `useIdentityListData` |
| `ego{id}.json` | EGO details | `useEGODetailData` |
| `egoSpecList.json` | All EGO specs | `useEGOListData` |
| `egoGift/{id}.json` | EGO Gift details | `useEGOGiftDetailData` |
| `egoGiftSpecList.json` | All EGO Gift specs (includes `recipe` field) | `useEGOGiftListData` |
| `themePackList.json` | Floor theme packs | `useThemePackListData` |
| `startBuff*.json` | Start buff data | `useStartBuffData` |
| `startGift*.json` | Start gift pools | `useStartGiftPools` |

### i18n (static/i18n/{lang}/)

| File | Contents |
|------|----------|
| `common.json` | UI strings (pages.plannerMD.* uses "egoGift" pattern, e.g., selectEgoGifts) |
| `identityNameList.json` | Identity names |
| `egoNameList.json` | EGO names |
| `egoGiftNameList.json` | EGO Gift names |
| `keywordMatch.json` | Keyword translations (EN, KR, JP) |
| `themePack.json` | Theme pack names |
| `sanityCondition.json` | Sanity condition templates |
| `seasons.json` | Season names (loaded by SeasonDropdown via useFilterI18nData) |
| `unitKeywords.json` | Unit keyword/affiliation names (loaded by UnitKeywordDropdown via useFilterI18nData) |
| `extraction.json` | Extraction calculator UI strings |
| `association.json` | Username association translations (Faust identity keywords) |

---

## Technology Stack Summary

### Frontend

| Layer | Technology |
|-------|------------|
| Framework | React 19 |
| Routing | TanStack Router (code-based) |
| Data Fetching | TanStack Query (useSuspenseQuery) |
| Validation | Zod |
| UI Components | shadcn/ui |
| Styling | Tailwind CSS |
| i18n | i18next |
| Rich Text | Tiptap |
| Build | Vite |

### Backend

| Layer | Technology |
|-------|------------|
| Framework | Spring Boot 4.0.0 |
| Language | Java 17 |
| Security | Spring Security + JWT |
| Database | MySQL + JPA/Hibernate |
| Build | Maven |

---

## Usage in Task Workflow

When starting a new task:

1. **Identify the feature domain** from the table above
2. **Read the core files** listed for that domain
3. **Check cross-cutting concerns** that might be affected
4. **Review the data flow** for the operation type
5. **Check high-impact files** if modifying shared code

This map should be read BEFORE diving into implementation details.
