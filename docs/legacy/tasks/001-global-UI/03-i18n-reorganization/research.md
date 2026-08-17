# Research: i18n Reorganization

## Clarifications Resolved

| Decision | Choice | Rationale |
|----------|--------|-----------|
| `filter.*` keys | Move to `database` | Groups with entity browsing pages |
| `egoGift.*` keys | Move to `database` | Primary use is EGO Gift browser; planner loads database namespace anyway |
| Language switch UX | Granular Suspense | Already implemented; each component shows loading state via Suspense boundary |

---

## Spec-to-Code Mapping

| Requirement | Files Affected | Change |
|-------------|----------------|--------|
| Split common.json | `static/i18n/{EN,JP,KR,CN}/common.json` | Remove database/planner keys |
| Create database.json | `static/i18n/{EN,JP,KR,CN}/database.json` | New file with sanity, passive, identity, filter, egoGift, pages.identity, pages.ego |
| Create planner.json | `static/i18n/{EN,JP,KR,CN}/planner.json` | New file with pages.plannerMD, pages.plannerList, deckBuilder |
| Update i18n.ts | `frontend/src/lib/i18n.ts` | Phase 1: add imports; Phase 2: remove static imports, add dynamic loading |
| Enable Suspense | `frontend/src/lib/i18n.ts` | Change `useSuspense: false` to `true` |
| Dynamic loading hook | `frontend/src/hooks/useI18nNamespace.ts` | New hook wrapping `addResourceBundle()` with query cache |
| Router preloading | `frontend/src/lib/router.tsx` | Add beforeLoad to entity/planner routes |

---

## Namespace Key Distribution

### common.json (always loaded, ~3KB)
- `header.*` (nav, settings, auth)
- `common.*` (confirm, cancel, close, reset, done, loading)
- `errors.*` (notFound, generic)
- `pages.info.*`, `pages.planner.*`, `pages.community.*` (placeholder pages)

### database.json (entity pages, ~3KB)
- `sanity.*` (sanity UI labels)
- `passive.*` (passive display labels)
- `identity.*` (unit keywords, status, resistances)
- `filter.*` (keywordless filter)
- `egoGift.*` (general, keyword, price, themePack, difficulty)
- `pages.identity.*` (browser labels)
- `pages.ego.*` (browser labels)

### planner.json (planner pages, ~3KB)
- `pages.plannerMD.*` (all planner UI, noteEditor, draftRecovery, save, conflict)
- `pages.plannerList.*` (list page, tabs, toolbar, sort, empty states, context menu)
- `deckBuilder.*` (export, import, edit deck)

### extraction.json (keep as-is, ~2KB)
- Already separate, no changes needed

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `useI18nNamespace.ts` | `hooks/useIdentityListData.ts` | queryOptions factory, queryKey pattern, staleTime, error handling |
| `database.json` | `static/i18n/EN/common.json` | JSON structure, nested keys |
| Router beforeLoad | `lib/router.tsx` | Route creation with lazyRouteComponent |

---

## Existing Utilities

| Category | Location | Reuse |
|----------|----------|-------|
| i18n singleton | `lib/i18n.ts` | Extend with `addResourceBundle()` calls |
| Dynamic fetch pattern | `hooks/useIdentityListData.ts` | Copy queryOptions factory pattern |
| Query client | `lib/queryClient.ts` | Reuse for namespace caching |
| Constants | `lib/constants.ts` | Add NAMESPACE_NAMES constant |

---

## Gap Analysis

**Currently Missing:**
- `useI18nNamespace()` hook
- `database.json` files (4 languages)
- `planner.json` files (4 languages)
- Router beforeLoad hooks for namespace preloading
- `useSuspense: true` in i18n config

**Needs Modification:**
- `lib/i18n.ts` - enable Suspense, Phase 2 remove static imports
- `lib/router.tsx` - add beforeLoad to: IdentityPage, EGOPage, EGOGiftPage, PlannerMDNewPage, PlannerListPage
- `static/i18n/*/common.json` - remove database + planner keys

**Can Reuse:**
- TanStack Query caching infrastructure
- Existing Suspense boundaries in route components
- `queryOptions()` pattern from data hooks

---

## Technical Constraints

- **i18next Suspense:** Must set `useSuspense: true` in config for granular loading
- **addResourceBundle():** Synchronous API - call after fetch completes
- **Query staleTime:** Set to Infinity (i18n doesn't change at runtime)
- **beforeLoad timing:** Executes before component mount - good for preloading
- **Phase 1 safe:** Static reorganization has no runtime behavior change

---

## Testing Requirements

### Manual UI Tests
- Phase 1: `yarn build` passes, all pages render, all 4 languages work
- Phase 2: Network tab shows namespace fetches on navigation
- Phase 2: Language switch shows Suspense fallbacks, then updates
- Phase 2: Slow 3G shows loading states correctly

### Automated Tests
- Build succeeds without i18next warnings
- All namespace files exist for all 4 languages
- No duplicate keys across namespace files
- All keys used in components exist in JSON files

### Edge Cases
- Network failure during namespace load → fallback to English + error toast
- Rapid language switches → latest wins, no race condition
- Missing key in namespace → shows key literal, logs warning
