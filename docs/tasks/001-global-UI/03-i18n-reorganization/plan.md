# Execution Plan: i18n Reorganization

## Planning Gaps

**None identified.** Research adequately covers all technical decisions.

**Assumptions documented:**
- CN/JP/KR common.json files have identical key structure to EN
- association.* keys stay in common.json (used in Header for username)
- Phase 1 completion is prerequisite for Phase 2

---

## Execution Overview

Two-phase implementation with clear verification between phases:

1. **Phase 1 (Static Reorganization)**: Split common.json into domain namespaces, update static imports. Zero runtime behavior change.
2. **Phase 2 (Dynamic Loading)**: Create dynamic loading hook, add router preloading, enable Suspense.

Phase 1 is safe rollback point. Phase 2 can be reverted independently.

---

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `static/i18n/{lang}/common.json` (x4) | Medium | None | All useTranslation() calls |
| `static/i18n/{lang}/database.json` (x4) | Low | Created from common.json | Entity pages |
| `static/i18n/{lang}/planner.json` (x4) | Low | Created from common.json | Planner pages |
| `frontend/src/lib/i18n.ts` | High | New namespace files | All components via i18next |
| `frontend/src/hooks/useI18nNamespace.ts` | Low | i18n.ts, queryClient | Router beforeLoad |
| `frontend/src/lib/router.tsx` | High | useI18nNamespace | All navigation |

### Ripple Effect Map

- common.json keys removed prematurely → UI shows {{key.path}} literals
- i18n.ts namespace list out of sync → translations fail
- useSuspense: true before router preloading → routes suspend on language change
- useI18nNamespace caching wrong → duplicate fetches

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| lib/i18n.ts | Core singleton | Phase 1: only add imports. Phase 2: verify preloading first |
| lib/router.tsx | All navigation | Non-throwing loader; fallback to English |
| common.json | Silent UI breakage | Extract keys AFTER new files exist |

---

## Execution Order

### Phase 1: Static Reorganization (Steps 1-5)

**1. Create database.json files (all 4 languages)**
- Action: Create static/i18n/{EN,JP,KR,CN}/database.json
- Keys: sanity.*, passive.*, identity.*, filter.*, egoGift.*, pages.identity.*, pages.ego.*
- Depends on: None
- Enables: F1

**2. Create planner.json files (all 4 languages)**
- Action: Create static/i18n/{EN,JP,KR,CN}/planner.json
- Keys: pages.plannerMD.*, pages.plannerList.*, deckBuilder.*
- Depends on: None
- Enables: F2

**3. Update i18n.ts with static imports**
- Action: Add imports for database/planner; add to ns array
- Depends on: Steps 1, 2
- Enables: Step 4

**4. Remove extracted keys from common.json**
- Action: Remove database/planner keys from all 4 common.json files
- Depends on: Step 3
- Enables: F3

**5. CHECKPOINT: Verify Phase 1**
- Action: yarn build + manual UI verification
- Depends on: Step 4
- Enables: Phase 2

### Phase 2: Dynamic Loading (Steps 6-10)

**6. Create useI18nNamespace hook**
- Action: Create frontend/src/hooks/useI18nNamespace.ts
- Pattern: Follow useIdentityListData.ts queryOptions pattern
- Depends on: Phase 1 complete
- Enables: F4

**7. Add router beforeLoad hooks**
- Action: Add beforeLoad to entity routes and planner routes
- Depends on: Step 6
- Enables: F5

**8. Update i18n.ts for dynamic loading**
- Action: Remove static imports for database/planner; set useSuspense: true
- Depends on: Step 7
- Enables: Step 9

**9. Add language change namespace loading**
- Action: Update Header.tsx changeLanguage to preload namespaces
- Depends on: Steps 6, 8
- Enables: F6

**10. CHECKPOINT: Verify Phase 2**
- Action: Network verification, slow 3G test, rapid language switch test
- Depends on: Step 9
- Enables: Done

---

## Verification Checkpoints

| After Step | Verify | Pass Criteria |
|------------|--------|---------------|
| 5 | yarn build | No missing translation warnings |
| 5 | Identity page (all 4 langs) | All labels render correctly |
| 5 | Planner page (all 4 langs) | All section headers render |
| 5 | Browser console | No i18next missing key warnings |
| 10 | Network tab - first load | Only common.json fetched on home |
| 10 | Network tab - entity page | database.json fetched |
| 10 | Network tab - planner page | planner.json fetched |
| 10 | Slow 3G | Loading states appear, then resolve |
| 10 | Rapid language switch | Latest selection wins |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Keys missing in new files | 1, 2 | Scripted extraction; diff verification |
| Duplicate keys across namespaces | 1, 2 | JSON lint + grep check |
| Build regression | 3, 4 | yarn build after each step |
| Language switch race condition | 9 | AbortController for pending loads |
| Network failure | 6, 7 | Fallback to English + toast |
| Router blocking | 7 | Non-blocking Promise in beforeLoad |

---

## Rollback Strategy

| Safe Point | How to Rollback | Data Loss |
|------------|-----------------|-----------|
| After Step 5 | Revert to Phase 1 commit | None |
| After Step 7 | Remove beforeLoad hooks | None |
| After Step 8 | Add static imports back | None |

**Emergency:** git revert to pre-Phase-2 commit restores full static loading.
