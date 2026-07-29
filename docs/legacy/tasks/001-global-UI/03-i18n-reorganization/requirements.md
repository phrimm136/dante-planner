# Task: i18n File Reorganization and Dynamic Loading

## Description

Reorganize the i18n JSON files from the current monolithic `common.json` structure into domain-specific namespaces, then implement dynamic loading to reduce initial bundle size.

**Current Problem:**
- `common.json` (8.5KB) contains ALL UI strings bundled together
- All 4 languages (EN/JP/KR/CN) are statically imported at app initialization
- Total i18n bundle: ~416KB loaded upfront regardless of which pages user visits
- This violates the codebase pattern where entity data (identity names, etc.) is already loaded dynamically

**Target Architecture:**

```
static/i18n/{lang}/
├── common.json      # Header, nav, buttons, errors (~3KB) - ALWAYS loaded statically
├── database.json    # Identity/EGO/Gift page UI - loaded on entity pages
├── planner.json     # Planner UI strings - loaded on planner pages
├── extraction.json  # Keep as-is - loaded on extraction page
└── battleKeywords.json  # Keep as-is (75KB, search-only)
```

**Two-Phase Implementation:**
1. **Phase 1 (Low Risk):** Static reorganization - split common.json into domain files, update imports
2. **Phase 2:** Add dynamic loading for non-common namespaces using `i18n.addResourceBundle()` pattern

**Namespace Mapping:**

| Target Namespace | Keys to Include | Source Section |
|------------------|-----------------|----------------|
| `common` | header.*, common.*, errors.* | Always needed |
| `database` | sanity.*, passive.*, identity.*, pages.identity.*, pages.ego.*, filter.* | Entity pages |
| `planner` | pages.plannerMD.*, pages.plannerList.*, deckBuilder.*, egoGift.* | Planner pages |
| `extraction` | (keep as-is) | Extraction page |

**Dynamic Loading Pattern:**
- Reuse TanStack Query patterns already in codebase (see `useIdentityListData.ts`)
- Use `i18n.addResourceBundle(lang, namespace, data)` for on-demand loading
- Router's `beforeLoad` hook preloads namespaces for target page
- Fallback to English if namespace load fails, show warning toast

## Research

- [ ] Review `frontend/src/lib/i18n.ts` - current initialization pattern
- [ ] Study `frontend/src/hooks/useIdentityListData.ts` - dynamic JSON loading pattern with TanStack Query
- [ ] Check i18next docs for `addResourceBundle()` and `hasResourceBundle()` APIs
- [ ] Analyze all `useTranslation()` calls to map components to namespaces
- [ ] Review TanStack Router `beforeLoad` hook for namespace preloading

## Scope

Files to READ for context:
- `frontend/src/lib/i18n.ts` - current i18n setup
- `frontend/src/hooks/useIdentityListData.ts` - dynamic fetch pattern to follow
- `frontend/src/routes/ExtractionPlannerPage.tsx` - example of namespace-specific page
- `static/i18n/EN/common.json` - current monolithic file structure
- `docs/architecture-map.md` - cross-cutting concerns section on i18n

## Target Code Area

**Phase 1 - Static Reorganization:**
- `static/i18n/{EN,JP,KR,CN}/common.json` - MODIFY (remove database/planner keys)
- `static/i18n/{EN,JP,KR,CN}/database.json` - CREATE
- `static/i18n/{EN,JP,KR,CN}/planner.json` - CREATE
- `frontend/src/lib/i18n.ts` - MODIFY (add new namespace imports)

**Phase 2 - Dynamic Loading:**
- `frontend/src/hooks/useI18nNamespace.ts` - CREATE (dynamic loading hook)
- `frontend/src/lib/i18n.ts` - MODIFY (remove static imports for database/planner)
- `frontend/src/lib/router.tsx` - MODIFY (add beforeLoad namespace preloading)
- `frontend/src/routes/IdentityPage.tsx` - MODIFY (ensure namespace loaded)
- `frontend/src/routes/PlannerMDNewPage.tsx` - MODIFY (ensure namespace loaded)

## System Context (Senior Thinking)

- **Feature domain:** Cross-cutting concern (i18n)
- **Core files in this domain:** `lib/i18n.ts`, `static/i18n/{lang}/*.json`
- **Cross-cutting concerns touched:**
  - i18n system (primary)
  - Router (for preloading)
  - All 86 components using `useTranslation()` (consumers)
- **Pattern to follow:** Dynamic JSON loading via TanStack Query (see `useIdentityListData.ts`)

## Impact Analysis

**Files being modified:**

| File | Impact Level | Notes |
|------|--------------|-------|
| `lib/i18n.ts` | High | Core i18n initialization, all components depend on it |
| `lib/router.tsx` | High | All navigation, add beforeLoad hooks |
| `static/i18n/*/common.json` | Medium | Breaking change if keys removed before code updated |
| New namespace files | Low | Additive only |

**What depends on these files:**
- 86 components call `useTranslation()` - automatically use whatever namespaces are loaded
- Language selector triggers `i18n.changeLanguage()` - must load all namespaces for new language

**Potential ripple effects:**
- If namespace not loaded before render: UI shows `{{key.path}}` literals
- Language switch delay: async load adds latency
- Missing keys in new files: breaks UI silently

**High-impact files to watch:**
- `lib/i18n.ts` - core singleton, must remain stable
- `lib/router.tsx` - navigation timing, must not block on slow namespace loads

## Risk Assessment

**Edge cases not yet defined:**
- What if namespace load fails mid-session? (Network error)
- What if user switches language rapidly during load?
- How to handle missing translations in new namespace files?

**Performance concerns:**
- Namespace load adds ~50-200ms latency on first visit to page
- Mitigation: preload in router beforeLoad hook (hidden by route transition)

**Backward compatibility:**
- Phase 1 is safe: just moving keys, all still statically loaded
- Phase 2 requires careful coordination: load namespace BEFORE component mounts

**Security considerations:**
- None - i18n files are public static assets

## Testing Guidelines

### Manual UI Testing

**Phase 1 - Static Reorganization:**
1. Run `yarn build` - verify no missing translation errors
2. Navigate to Identity page - verify all labels render correctly
3. Navigate to EGO page - verify all labels render correctly
4. Navigate to Planner page - verify all section headers render
5. Navigate to Extraction page - verify calculator labels work
6. Switch language to JP - verify all pages still work
7. Switch language to KR - verify all pages still work
8. Switch language to CN - verify all pages still work
9. Check browser console for any i18next warnings about missing keys

**Phase 2 - Dynamic Loading:**
1. Clear browser cache and localStorage
2. Open Network tab in DevTools
3. Navigate to Home page - verify only common.json fetched
4. Navigate to Identity page - verify database.json fetched
5. Navigate back to Home - verify no re-fetch (cached)
6. Navigate to Planner page - verify planner.json fetched
7. Switch language to JP:
   - Verify common-JP loads
   - Verify database-JP loads (if on entity page)
8. Navigate to page during language load - verify graceful handling
9. Throttle network to Slow 3G - verify loading states appear
10. Disconnect network, switch language - verify error toast appears

### Automated Functional Verification

- [ ] Build succeeds: `yarn build` completes without errors
- [ ] TypeScript: No type errors related to translation keys
- [ ] All namespaces exist: Each language has common.json, database.json, planner.json, extraction.json
- [ ] No duplicate keys: Each key exists in exactly one namespace
- [ ] No orphan keys: All keys in JSON files are used in code

### Edge Cases

- [ ] Missing namespace: Component renders with fallback (English or key literal)
- [ ] Network failure: Error toast shown, app continues working
- [ ] Rapid language switch: Latest selection wins, no race condition
- [ ] Empty namespace response: Graceful fallback, no crash
- [ ] Invalid JSON response: Error logged to Sentry, fallback to English

### Integration Points

- [ ] Router integration: beforeLoad correctly preloads namespaces
- [ ] Language selector: changeLanguage loads all needed namespaces
- [ ] TanStack Query cache: Namespaces cached with appropriate staleTime
- [ ] Error boundary: Translation errors don't crash entire page
