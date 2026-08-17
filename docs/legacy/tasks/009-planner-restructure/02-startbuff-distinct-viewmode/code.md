# Code Documentation: StartBuff Summary/Edit Mode Separation

## Implementation Summary

Refactored StartBuff section from a dual-purpose `viewMode` pattern to a clean Summary + EditPane separation, following game UX conventions.

## Files Changed

### Phase 1: Foundation

| File | Change |
|------|--------|
| `frontend/src/lib/constants.ts` | Added `CURRENT_MD_VERSION = 6`, `MDVersion` type, `MD_ACCENT_COLORS` mapping |
| `frontend/src/lib/assetPaths.ts` | Added `getStartBuffMiniPath(version)`, `getStartBuffMiniHighlightPath(version)` |

### Phase 2: New Component

| File | Change |
|------|--------|
| `frontend/src/components/startBuff/StartBuffMiniCard.tsx` | **NEW** - Compact 96×96px card for summary display |
| `frontend/src/components/startBuff/StartBuffMiniCard.test.tsx` | **NEW** - 15 unit tests for mini card |

### Phase 3: Refactor

| File | Change |
|------|--------|
| `frontend/src/components/startBuff/StartBuffCard.tsx` | Removed `viewMode` prop and related conditionals (edit-only now) |
| `frontend/src/components/startBuff/StartBuffSection.tsx` | Refactored to render mini cards for selected buffs in summary |
| `frontend/src/components/startBuff/StartBuffEditPane.tsx` | Removed `viewMode={false}` prop from StartBuffCard |
| `frontend/src/components/startBuff/StartBuffCard.test.tsx` | Updated tests for viewMode removal |
| `frontend/src/components/startBuff/StartBuffSection.test.tsx` | Rewrote tests for mini card behavior |
| `frontend/src/components/startBuff/StartBuffEditPane.test.tsx` | Updated tests for viewMode removal |

### Phase 4: Integration

| File | Change |
|------|--------|
| `frontend/src/routes/PlannerMDNewPage.tsx` | Removed `viewMode={true}` prop from StartBuffSection |
| `static/i18n/EN/common.json` | Added `selectStartBuffs` i18n key for empty state |

## Verification Results

### TypeScript Compilation
```
✅ yarn tsc --noEmit - PASS
```

### Unit Tests
```
✅ 33 tests passed (4 files)
- StartBuffMiniCard.test.tsx: 15 tests
- StartBuffCard.test.tsx: 4 tests
- StartBuffSection.test.tsx: 8 tests
- StartBuffEditPane.test.tsx: 6 tests
```

### Code Review
```
Verdict: ACCEPTABLE
- Spec compliance: 100%
- Pattern compliance: 100%
- No critical/major issues
```

## Architecture Decisions

### 1. Version-Aware Constants
Instead of hardcoding `MD6_ACCENT_COLOR`, implemented `MD_ACCENT_COLORS[CURRENT_MD_VERSION]` for future MD version support.

### 2. Component Separation
- `StartBuffMiniCard`: Display-only (summary view)
- `StartBuffCard`: Interactive (edit view)
- Follows SRP - each component has single responsibility

### 3. Enhancement Indicator Reuse
Imported `EGOGiftEnhancementIndicator` instead of duplicating logic - follows DRY principle.

## Known Limitations

1. **Mini card text truncation**: Long buff names may be cut off. Mitigated with `text-ellipsis` + `overflow-hidden`.

2. **Inline color style**: Uses `style={{ color: accentColor }}` for dynamic theming. Acceptable pattern for runtime color selection.
