---
paths:
  - "frontend/src/hooks/**/*.ts"
---

# Hook Conventions

## Naming

```
use[Domain][Action?]

useIdentityDetailData    ✓  (domain + action)
useEGOFilterState        ✓  (domain + concern)
usePlannerConfig         ✓  (domain)
useData                  ✗  (too vague)
fetchIdentity            ✗  (not a hook name)
```

## Return Types

| Values returned | Pattern | Example |
|---|---|---|
| 2 or fewer | Tuple | `return [value, setValue] as const` |
| 3 or more | Named object | `return { data, isLoading, error, refetch }` |

Always annotate return type explicitly for public hooks:

```typescript
// Good — caller knows the contract
function usePlannerConfig(): {
  config: PlannerConfig
  updateConfig: (patch: Partial<PlannerConfig>) => void
} {
  // ...
}

// Bad — caller infers, refactors break silently
function usePlannerConfig() { ... }
```

## Mandatory Rules

- **Single responsibility** — one hook, one domain concern
- **Compose hooks** — `useIdentityPage` calls `useIdentityDetailData` + `useFilterState`
- **No conditional hook calls** — Rules of Hooks, not negotiable
- **Explicit deps** — put only primitives in dependency arrays; objects cause stale closure bugs

## Forbidden Patterns

| Forbidden | Use Instead |
|---|---|
| `useEffect` to sync state derived from props | Compute during render or `useMemo` |
| `useEffect` with empty deps as "mount" | Query hooks or router loaders |
| Hook that returns JSX | Separate into hook + component |
| Mixing server state + client state in one hook | Separate query hook from UI state hook |

## File Location

```
src/hooks/useSharedHook.ts        # cross-feature hooks
src/hooks/useEntityListData.ts    # data hooks alongside each other
```

## Composition Pattern

```typescript
// Composed hook — aggregates domain concerns
function useIdentityPage() {
  const { data, isLoading } = useIdentityListData()
  const { filters, setFilters } = useFilterState()
  const filtered = useFilteredData(data, filters)

  return { filtered, isLoading, filters, setFilters }
}
```

**Reference:** `hooks/useEntityListData.ts`, `hooks/useEntityDetailData.ts`
