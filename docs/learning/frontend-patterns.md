# Frontend Patterns - Educational Reference

This document explains **why** LimbusPlanner uses specific patterns. For actionable rules, see the `fe-*` skills.

---

## React Compiler

### Why No Manual Memoization?

React Compiler (babel-plugin-react-compiler) analyzes code at build time and automatically inserts memoization. This eliminates the need for manual `memo`, `useMemo`, and `useCallback`.

**Verification:** Open React DevTools, look for "Memo" badge next to component names.

**Opt out (rare):** Add `"use no memo"` directive to problematic components.

---

## Why Not React.FC?

| Issue | Explanation |
|-------|-------------|
| Implicit children | Pre-React 18, FC included children implicitly |
| Syntax inflexibility | Hard to apply to named functions |
| No significant benefit | TypeScript 5.1+ explicit typing is equally powerful |

Use explicit props interface instead:
```typescript
interface Props { title: string }
export function Component({ title }: Props) { ... }
```

---

## Suspense vs Manual Loading

**Old pattern (waterfall):**
```typescript
const q1 = useQuery(...)
const q2 = useQuery({ enabled: q1.isSuccess })
if (q1.isPending) return <Loading />
```

**New pattern (parallel):**
```typescript
<Suspense fallback={<Loading />}>
  <Content />  // useSuspenseQueries inside
</Suspense>
```

Benefits:
- Parallel loading (no waterfall)
- `data` always defined (no undefined checks)
- SSR-ready

---

## Set Mutation Safety

React state updates require **new references** to trigger re-render:

```typescript
// Wrong - same reference, no re-render
selectedIds.add(newId)
setSelectedIds(selectedIds)

// Correct - new Set triggers re-render
setSelectedIds(prev => new Set([...prev, newId]))
```

---

## useLayoutEffect vs useEffect

| Hook | Use Case |
|------|----------|
| `useLayoutEffect` | DOM measurements affecting layout (prevents flicker) |
| `useEffect` | Side effects (API calls, subscriptions) |

---

## Mobile Breakpoint (lg: 1024px)

LimbusPlanner uses `lg` as primary mobile/desktop boundary because:
- Tablet landscape (1024px) benefits from simplified layouts
- Aligns with typical "sidebar visible" threshold
- Comfortable desktop experience for common laptops

---

## Composition Patterns Overview

| Pattern | When to Use |
|---------|-------------|
| Compound Components | UI primitives (Tabs, Dialog) |
| Children Slot | Add behavior to any component (TierLevelSelector) |
| Named Slots | Layout with distinct regions (DetailPageLayout) |
| Provider + Consumer | Global state (Theme, Auth) |
| SectionContainer | Multi-section pages (PlannerMDNewPage) |

---

## cn() vs Template Literals

```typescript
// Wrong - conflicting classes don't merge
`p-4 ${isLarge ? 'p-8' : ''}`  // Results in "p-4 p-8"

// Correct - tailwind-merge handles conflicts
cn('p-4', isLarge && 'p-8')    // Results in "p-8"
```

---

## Data Fetching Strategy

| Strategy | When to Use |
|----------|-------------|
| Receive props | Data shared with parent/siblings |
| Fetch internally | Self-contained, independent data |

Decision flow: If parent needs data OR siblings use it → lift to parent. Otherwise → fetch internally.

---

## Further Reading

- [React Compiler Installation](https://react.dev/learn/react-compiler/installation)
- [TanStack Query Suspense](https://tanstack.com/query/latest/docs/react/guides/suspense)
- [TypeScript React Cheatsheets](https://react-typescript-cheatsheet.netlify.app/)
