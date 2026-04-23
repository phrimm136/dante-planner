---
paths:
  - "frontend/src/components/**/*.tsx"
  - "frontend/src/components/**/*.ts"
---

# Component State Management Patterns

## Decision Tree

```
Is this data from the server?
  YES → TanStack Query (useQuery / useMutation)
  NO  → Is it local to one component?
    YES → useState / useReducer
    NO  → Shared across 2-3 nearby components?
      YES → Prop drilling or Context
      NO  → Global, persistent, or complex client state?
        YES → Zustand
```

## State Type Selection

| State Type | Solution | Examples |
|---|---|---|
| Server data (API) | TanStack Query | API responses, cache, optimistic updates |
| Local UI state | `useState` | Modal open, tab selection, form draft |
| Theme, i18n | Context (existing) | Stable, rarely-changing data |
| Complex shared UI state | Zustand with selectors | Planner equipment, SSE status, user prefs |
| **High-frequency UI state** (per-frame, progressive counters, anything updated in `requestAnimationFrame` chains) | **Zustand with atomic selector; subscribers only** | Progressive-render counts, scroll ticks, animation frames |

**Why high-frequency state must live in Zustand, not `useState`:** `useState` in a parent cascades re-renders through every child on each update, even memoized ones (reconciliation pass runs regardless). With Zustand, only the atomic selector's consumer re-renders — the parent stays frozen. For rAF-driven state updated ~60 times per second, this is the difference between the whole subtree reconciling 60× per second vs. one leaf component re-rendering 60× per second.

Precedent: `deckVisibleCount` in `usePlannerEditorStore`, consumed only by `IdentityGrid` / `EgoGrid` via `useDeckVisibleCount()`. The progressive rAF chain in `DeckBuilderContent` writes via `storeApi.getState().setDeckVisibleCount(...)` imperatively — no React subscription in the writer's scope.

## Zustand Rules

```typescript
// GOOD: Atomic selector — only re-renders when this slice changes
const equipment = usePlannerStore((s) => s.equipment[sinner])
const setEquipment = usePlannerStore((s) => s.setEquipment)

// BAD: Re-renders on ANY store change
const store = usePlannerStore()

// BAD: Object literal selector — new reference every render
const { user, token } = useAuthStore()
```

- Use atomic selectors: `useStore(s => s.field)` not `useStore()`
- Export custom hooks (`useAuthStore`, `useSseStore`) not the raw store
- Do NOT put server data in Zustand — that's TanStack Query's job
- Do NOT mutate state outside store actions

## Context Rules

- Context is for stable, rarely-changing data: theme, locale, auth user info
- Context triggers re-renders for ALL consumers on any change
- Do NOT put frequently-changing state in Context
- Break large providers: `useAuth` (hook) + `AuthContext` (context) + `AuthGuard` (boundary)

## Forbidden Patterns

| Forbidden | Use Instead |
|---|---|
| API data in Zustand | TanStack Query |
| `useStore()` without selector | `useStore(s => s.field)` |
| Frequent state in Context | Zustand with selectors |
| Prop drilling 3+ levels | Context or Zustand |

**Reference:** `stores/usePlannerStore.ts`
