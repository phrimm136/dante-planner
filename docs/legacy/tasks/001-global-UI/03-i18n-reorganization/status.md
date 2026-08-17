# Status: i18n Reorganization

## Execution Progress

Last Updated: 2026-01-07
Current Step: 5/10
Current Phase: Phase 1 Complete

### Milestones
- [x] M1: Phase 1 - Static reorganization complete (Steps 1-5)
- [~] M2: Phase 2 - Dynamic loading (DEFERRED to docs/TODO.md PERF-001)

### Step Log
- Step 1: ✅ database.json creation (x4 languages)
- Step 2: ✅ planner.json creation (x4 languages)
- Step 3: ✅ i18n.ts static import update
- Step 4: ✅ common.json key removal
- Step 5: ✅ CHECKPOINT Phase 1 verification
- Step 6: ⏳ useI18nNamespace hook creation
- Step 7: ⏳ Router beforeLoad hooks
- Step 8: ⏳ i18n.ts dynamic loading + useSuspense
- Step 9: ⏳ Language change namespace loading
- Step 10: ⏳ CHECKPOINT Phase 2 verification

---

## Feature Status

### Core Features
- [x] F1: database.json exists for all 4 languages
- [x] F2: planner.json exists for all 4 languages
- [x] F3: common.json contains only global keys
- [ ] F4: Dynamic namespace loading via TanStack Query
- [ ] F5: Router preloads namespaces in beforeLoad
- [ ] F6: Language switch loads required namespaces

### Edge Cases
- [ ] E1: Network failure shows fallback + toast
- [ ] E2: Rapid language switch - latest wins
- [ ] E3: Missing key - key literal + console warning
- [ ] E4: Invalid JSON - error logged, English fallback

---

## Testing Checklist

### Automated Tests
- [ ] yarn build passes with no warnings
- [ ] yarn lint passes
- [ ] yarn typecheck passes
- [ ] All 4 languages have database.json, planner.json

### Manual Verification (Phase 1)
- [x] Identity page: all labels render (KR verified)
- [x] EGO page: all labels render (KR verified)
- [x] EGO Gift page: all labels render (KR verified)
- [x] Planner page: all section headers render (KR verified)
- [x] Browser console: no missing key warnings

### Manual Verification (Phase 2)
- [ ] Home page: only common.json in Network tab
- [ ] Identity page: database.json fetched
- [ ] Planner page: planner.json fetched
- [ ] Language switch: EN → JP → KR → CN cycle works
- [ ] Slow 3G: loading states visible
- [ ] Offline: error toast appears

---

## Summary

| Metric | Status |
|--------|--------|
| Steps | 5/10 complete |
| Milestones | 1/2 complete (M2 deferred) |
| Features | 3/6 verified (F1-F3) |
| Edge Cases | 0/4 verified (Phase 2 scope) |
| Phase | Phase 1 Complete |
