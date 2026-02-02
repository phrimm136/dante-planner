---
paths:
  - "frontend/src/components/**/*.tsx"
  - "frontend/src/components/**/*.ts"
---

# Component State Management Patterns

## State Type Selection

| State Type | Solution |
|------------|----------|
| Server data (API) | TanStack Query |
| Complex shared UI state | Zustand with selectors |
| Theme, i18n | Context (existing) |
| Local component state | `useState` |

## Zustand Pattern (prevents re-render cascade)

```typescript
// GOOD: Only re-renders when this slice changes
const equipment = usePlannerStore((s) => s.equipment[sinner])
const setEquipment = usePlannerStore((s) => s.setEquipment)

// BAD: Re-renders on ANY store change
const store = usePlannerStore()
```

## Provider + Consumer (theme, i18n only)

```typescript
<ThemeProvider>
  <App />  {/* useTheme() anywhere inside */}
</ThemeProvider>
```

**Reference:** `stores/usePlannerStore.ts`
