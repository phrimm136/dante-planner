# LimbusPlanner Architecture Map

> **Purpose:** Provide architectural context for AI-assisted development. Read this before diving into implementation details.
>
> **Last Updated:** 2026-01-02

---

## Quick Reference: Core Files by Feature

### Frontend Core Files

| Feature | Core Files | Supporting Files |
|---------|------------|------------------|
| **Identity Browser** | `routes/IdentityPage.tsx`, `routes/IdentityDetailPage.tsx` | `hooks/useIdentityListData.ts`, `components/identity/*` |
| **EGO Browser** | `routes/EGOPage.tsx`, `routes/EGODetailPage.tsx` | `hooks/useEGOListData.ts`, `components/ego/*` |
| **EGO Gift Browser** | `routes/EGOGiftPage.tsx`, `routes/EGOGiftDetailPage.tsx` | `hooks/useEGOGiftListData.ts`, `components/egoGift/*` |
| **Detail Page Layout** | `components/common/DetailPageLayout.tsx` | `DetailEntitySelector.tsx`, `DetailLeftPanel.tsx`, `DetailRightPanel.tsx`, `MobileDetailTabs.tsx` |
| **Planner (MD)** | `routes/PlannerMDNewPage.tsx` | `hooks/usePlannerStorage.ts`, `components/deckBuilder/*` (Summary+Pane pattern), `components/startBuff/*` (Summary+EditPane pattern), `components/startGift/*`, `components/floorTheme/*`, `components/noteEditor/*` |
| **Planner Sync** | `hooks/usePlannerSync.ts` | `hooks/usePlannerStorageAdapter.ts`, `hooks/usePlannerMigration.ts`, `lib/plannerApi.ts` |
| **Filter Sidebar** | `components/common/FilterSidebar.tsx` | `FilterPageLayout.tsx`, `FilterSection.tsx`, `CompactIconFilter.tsx` |
| **Sanity Condition** | `lib/sanityConditionFormatter.ts` | `hooks/useSanityConditionData.ts` |
| **Authentication** | `routes/auth/callback/google.tsx` | `lib/api.ts`, `hooks/useAuthQuery.ts` |

### Backend Core Files

| Domain | Core Files | Supporting Files |
|--------|------------|------------------|
| **Authentication** | `controller/AuthController.java` | `service/JwtService.java`, `service/GoogleOAuthService.java`, `security/JwtAuthenticationFilter.java` |
| **User Management** | `service/UserService.java` | `repository/UserRepository.java`, `entity/User.java` |
| **Planner CRUD** | `controller/PlannerController.java`, `service/PlannerService.java` | `repository/PlannerRepository.java`, `entity/Planner.java`, `service/PlannerSseService.java`, `dto/planner/*` |
| **Planner Publishing** | `service/PlannerService.java` (togglePublish, castVote) | `entity/PlannerVote.java`, `entity/VoteType.java`, `repository/PlannerVoteRepository.java`, `dto/planner/PublicPlannerResponse.java`, `dto/planner/VoteRequest.java`, `converter/KeywordSetConverter.java` |
| **Planner View Tracking** | `service/PlannerService.java` (recordView) | `entity/PlannerView.java`, `entity/PlannerViewId.java`, `repository/PlannerViewRepository.java`, `util/ViewerHashUtil.java` |
| **Configuration** | `config/SecurityConfig.java`, `config/WebConfig.java` | `config/CorsConfig.java`, `config/DeviceIdArgumentResolver.java`, `config/RateLimitConfig.java` |
| **Exception Handling** | `exception/GlobalExceptionHandler.java` | `exception/PlannerNotFoundException.java`, `exception/PlannerConflictException.java`, `exception/PlannerForbiddenException.java`, `exception/PlannerValidationException.java`, `exception/UserNotFoundException.java`, `exception/RateLimitExceededException.java` |
| **Validation** | `validation/PlannerContentValidator.java` | `validation/SinnerIdValidator.java`, `validation/GameDataRegistry.java` |

---

## Cross-Cutting Concerns

### Where Things Live

| Concern | Frontend Location | Backend Location |
|---------|-------------------|------------------|
| **Validation** | `schemas/*.ts` (Zod) | DTOs with Jakarta annotations |
| **i18n** | `lib/i18n.ts`, `static/i18n/{lang}/*.json` | N/A |
| **Theme** | `contexts/ThemeContext.tsx` | N/A |
| **Auth Tokens** | HttpOnly cookies (managed by backend) | `JwtService.java` |
| **API Client** | `lib/api.ts`, `lib/plannerApi.ts` | N/A |
| **Constants** | `lib/constants.ts` | `application.properties` |
| **Asset Paths** | `lib/assetPaths.ts` | N/A |
| **Error Handling** | `components/common/ErrorBoundary.tsx` | `exception/GlobalExceptionHandler.java` |
| **Section Layout** | `components/common/PlannerSection.tsx` | N/A |
| **Card Grid Layout** | `components/common/ResponsiveCardGrid.tsx` | N/A |
| **Entity Sorting** | `lib/entitySort.ts` | N/A |
| **Sanity Formatting** | `lib/sanityConditionFormatter.ts` | N/A |
| **Keyword Formatting** | `lib/keywordFormatter.ts`, `components/common/FormattedDescription.tsx` | N/A |
| **Filter Layout** | `components/common/FilterSidebar.tsx`, `FilterPageLayout.tsx` | N/A |
| **Real-time Sync** | `hooks/usePlannerSync.ts` (SSE) | `service/PlannerSseService.java` |
| **Rate Limiting** | N/A | `config/RateLimitConfig.java` (Bucket4j) |
| **Content Validation** | `schemas/PlannerSchemas.ts` | `validation/PlannerContentValidator.java` |
| **Device Identification** | `lib/api.ts` (deviceId header) | `config/DeviceIdArgumentResolver.java` |

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
    │<─[9] Set Cookies─────────┤                           │
```

**Key Files:**
- `controller/AuthController.java` (steps 4, 9)
- `service/GoogleOAuthService.java` (steps 5-8)
- `service/JwtService.java` (token generation)

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
- `GET /api/planner/md/published` - browse all published planners
- `GET /api/planner/md/recommended` - planners with net votes >= threshold

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

**Sorting:**
- `sortByReleaseDate()` in `lib/entitySort.ts`: updateDate DESC → id DESC (newest first)
- Applied to Identity and EGO lists after filtering

**Pattern Files to Reference:**
- List page: `routes/IdentityPage.tsx`
- Detail page: `routes/IdentityDetailPage.tsx`
- List data hook: `hooks/useIdentityListData.ts`
- Detail data hook: `hooks/useIdentityDetailData.ts`
- Card grid: `components/common/ResponsiveCardGrid.tsx`
- Sort utility: `lib/entitySort.ts`
- Filter layout: `components/common/FilterPageLayout.tsx`, `FilterSidebar.tsx`
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
- Implementation: `routes/IdentityDetailPage.tsx`

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
│   ├── StartGift Section                                 │
│   │     └── StartGiftRow                               │
│   ├── EGO Gift Observation Section                      │
│   │     └── EGOGiftObservationCard                     │
│   ├── Comprehensive EGO Gift Section                    │
│   │     └── EGOGiftSelectionList                       │
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

**State Management:**
- ~15 useState hooks for different sections
- Lifted filter state: `DeckFilterState` for DeckBuilder pane persistence
- Auto-save: `hooks/usePlannerAutosave.ts` (2-second debounce)
- Persistence: `hooks/usePlannerStorage.ts` (IndexedDB)

**Key Data Hooks:**
- `useStartBuffData.ts`
- `useStartGiftPools.ts`
- `useEGOGiftObservationData.ts`
- `useEGOGiftListData.ts`
- `useThemePackListData.ts`

---

## File Dependency Graph

### Frontend Dependencies

```
main.tsx
    └── lib/router.tsx
          └── routes/*Page.tsx
                ├── hooks/use*Data.ts
                │     ├── schemas/*Schemas.ts
                │     └── lib/validation.ts
                ├── components/common/FilterPageLayout.tsx
                │     ├── FilterSidebar.tsx
                │     └── FilterSection.tsx
                ├── components/{domain}/*List.tsx
                │     ├── lib/entitySort.ts (sortByReleaseDate)
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
                └── lib/constants.ts (DETAIL_PAGE, SANITY_INDICATOR_COLORS)
```

### Backend Dependencies

```
BackendApplication.java
    ├── config/SecurityConfig.java
    │     └── security/JwtAuthenticationFilter.java
    │           └── service/JwtService.java
    └── config/WebConfig.java
          └── config/DeviceIdArgumentResolver.java

controller/AuthController.java
    ├── service/UserService.java
    │     └── repository/UserRepository.java
    ├── service/JwtService.java
    └── service/GoogleOAuthService.java

controller/PlannerController.java
    ├── config/RateLimitConfig.java (Bucket4j rate limiting)
    ├── service/PlannerService.java (configurable via @Value)
    │     ├── repository/PlannerRepository.java
    │     │     ├── entity/Planner.java
    │     │     └── Atomic vote methods (incrementUpvotes, decrementUpvotes, etc.)
    │     ├── repository/PlannerVoteRepository.java
    │     │     └── entity/PlannerVote.java (@IdClass: PlannerVoteId)
    │     │           └── entity/VoteType.java (enum: UP, DOWN)
    │     ├── validation/PlannerContentValidator.java (@Value size limits)
    │     │     ├── validation/GameDataRegistry.java
    │     │     └── validation/SinnerIdValidator.java
    │     └── converter/KeywordSetConverter.java (MySQL SET)
    └── service/PlannerSseService.java (SSE + zombie cleanup, DEBUG logs)

exception/GlobalExceptionHandler.java (hybrid error handling)
    └── exception/*Exception.java (Planner*, User*, RateLimit*)

dto/planner/PublicPlannerResponse.java (PII protection: always "Anonymous")
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
| `service/PlannerService.java` | High | All planner CRUD and sync |
| `config/RateLimitConfig.java` | High | All rate-limited endpoints |
| `validation/PlannerContentValidator.java` | High | All planner create/update |
| `exception/GlobalExceptionHandler.java` | High | All error responses |

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
| `components/common/FilterSidebar.tsx` | Medium | IdentityPage, EGOPage, EGOGiftPage |
| `components/common/FilterPageLayout.tsx` | Medium | All list pages |
| `dto/planner/PublicPlannerResponse.java` | Medium | All public planner endpoints |

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
| `egoGiftSpecList.json` | All EGO Gift specs | `useEGOGiftListData` |
| `themePackList.json` | Floor theme packs | `useThemePackListData` |
| `startBuff*.json` | Start buff data | `useStartBuffData` |
| `startGift*.json` | Start gift pools | `useStartGiftPools` |

### i18n (static/i18n/{lang}/)

| File | Contents |
|------|----------|
| `common.json` | UI strings |
| `identityNameList.json` | Identity names |
| `egoNameList.json` | EGO names |
| `egoGiftNameList.json` | EGO Gift names |
| `keywordMatch.json` | Keyword translations |
| `themePack.json` | Theme pack names |
| `sanityCondition.json` | Sanity condition templates |
| `seasons.json` | Season names |

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
