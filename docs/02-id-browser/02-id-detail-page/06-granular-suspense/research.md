# Research: Granular Suspense for Identity Detail Page

## Spec Ambiguities
**NONE DETECTED** - Spec is well-defined with clear acceptance criteria.

---

## Spec-to-Code Mapping

| Requirement | File | Action |
|-------------|------|--------|
| Split hook into spec/i18n | `useIdentityDetailData.ts` | Add `useIdentityDetailSpec(id)` + `useIdentityDetailI18n(id)` |
| Header name skeleton | `IdentityHeaderI18n.tsx` | NEW component |
| Skill names/descriptions | `SkillI18n.tsx` | NEW component |
| Passive names/descriptions | `PassiveI18n.tsx` | NEW component |
| Panic info skeleton | `SanityI18n.tsx` | NEW component wrapping `usePanicInfo` |
| Trait names skeleton | `TraitsDisplay.tsx` | REFACTOR from useEffect to Suspense |
| Page layout stays visible | `IdentityDetailPage.tsx` | Use spec hook in shell, wrap i18n in Suspense |

---

## Spec-to-Pattern Mapping

| Requirement | Source Pattern | Location |
|-------------|----------------|----------|
| Split spec/i18n hook | Paired hooks pattern | `useIdentityListData.ts` |
| Micro-Suspense boundary | Card + Name pattern | `IdentityCard.tsx` + `IdentityName.tsx` |
| Suspending component | Internal hook pattern | `IdentityName.tsx` |
| Dropdown Suspense | Component + hook | `SeasonDropdown.tsx` + `useFilterI18nData.ts` |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `IdentityHeaderI18n.tsx` | `IdentityName.tsx` | Suspending component, skeleton sizing |
| `SkillI18n.tsx` | `IdentityName.tsx` | Suspending + `FormattedDescription` |
| `PassiveI18n.tsx` | `IdentityName.tsx` | Suspending component |
| `SanityI18n.tsx` | `usePanicInfo.ts` + `IdentityName.tsx` | Wrap existing suspending hook |
| `TraitsI18n.tsx` | `useIdentityListI18n.ts` | New hook + suspending component |
| Hook exports | `useIdentityListData.ts` | Query key factory + paired hooks |

---

## Existing Utilities

| Category | Location | Functions |
|----------|----------|-----------|
| Schemas | `schemas/IdentitySchemas.ts` | `IdentityDataSchema`, `IdentityI18nSchema` |
| Query Keys | `hooks/useIdentityDetailData.ts` | `identityDetailQueryKeys.{all, detail, i18n}` |
| Panic Hook | `hooks/usePanicInfo.ts` | `usePanicInfo()`, `getPanicEntry()` |
| Asset Paths | `lib/assetPaths.ts` | `getPanicIconPath()`, `getAffinityIconPath()` |
| Formatters | `components/common/FormattedDescription.tsx` | Rich text rendering |
| Skeleton | `components/ui/skeleton.tsx` | `Skeleton` component |

---

## Gap Analysis

### Currently Missing
- `useIdentityDetailSpec(id)` - Spec-only hook (no language key)
- `useIdentityDetailI18n(id)` - I18n-only hook (language in key)
- `IdentityHeaderI18n.tsx` - Suspending name component
- `SkillI18n.tsx` - Suspending skill display
- `PassiveI18n.tsx` - Suspending passive display
- `SanityI18n.tsx` - Suspending panic info component
- `useTraitsI18n.ts` - Hook for `unitKeywords.json`

### Needs Modification
- `useIdentityDetailData.ts` - Keep original, add new exports
- `TraitsDisplay.tsx` - Convert useEffect→Suspense pattern
- `IdentityDetailPage.tsx` - Use spec hook + Suspense boundaries

### Can Reuse
- `usePanicInfo()` - Already suspends
- `Skeleton` component - Used in IdentityCard
- `FormattedDescription` - Skill text rendering
- `identityDetailQueryKeys` - Query key factory

---

## Testing Requirements

### Manual UI Tests
1. Initial load → Full `DetailPageSkeleton` (unchanged)
2. Language switch EN→KR → Layout visible, i18n shows skeletons
3. Cached language → Instant update, no skeleton
4. Rapid switching → No race conditions
5. Mobile tabs → Granular loading per tab

### Automated Verification
- Spec query key has NO language
- I18n query key HAS language
- Skeleton dimensions match actual content
- Static labels visible during load

### Edge Cases
- Network timeout on i18n fetch
- Missing i18n data for identity
- Rapid route navigation during load
- Mobile tab switching while suspending

---

## Technical Constraints

| Constraint | Mitigation |
|------------|------------|
| React Compiler | Keep components simple, trust compiler |
| Query Cache | Use factory `identityDetailQueryKeys.detail()` + `.i18n()` |
| Suspense Boundaries | Each i18n component gets own `<Suspense>` |
| Backwards Compatibility | Keep `useIdentityDetailData()`, mark deprecated |
| Skeleton Sizing | Derive from actual content dimensions |

---

## Implementation Order

1. `useIdentityListData.ts` - Reference for paired hook pattern
2. `IdentityCard.tsx` + `IdentityName.tsx` - Micro-Suspense pattern
3. `usePanicInfo.ts` - Already suspending, needs wrapper
4. `IdentityDetailPage.tsx` - Current route to refactor
5. `TraitsDisplay.tsx` - useEffect pattern to convert

**New Files:**
- `hooks/useTraitsI18n.ts`
- `components/identity/IdentityHeaderI18n.tsx`
- `components/identity/SkillI18n.tsx`
- `components/identity/PassiveI18n.tsx`
- `components/identity/SanityI18n.tsx`
- `components/identity/TraitsI18n.tsx`
