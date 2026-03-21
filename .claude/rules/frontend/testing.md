---
paths:
  - "frontend/src/**/__tests__/**"
---

# Frontend Testing Core

## Test Type Balance

| Type | When | Tool |
|---|---|---|
| Unit | Pure functions, utilities, formatters | Vitest |
| Hook | Hooks with complex logic, async, context | `renderHook` |
| Integration | Components with interactions, queries, mutations | RTL + MSW |
| E2E | Critical user flows | Playwright (separate) |

Favor integration over unit tests. Test behavior from the user's perspective.

## Mandatory Rules

- **Test behavior, not implementation** — user interactions, not internal state
- **Use `screen` queries** — not container queries
- **Query by role/label/text** — never by CSS class or component name
- **Wrap state updates** — use RTL's async utilities
- **Mock at network level** (MSW) for integration tests, not function level

## Query Priority (Testing Library)

```
getByRole       → primary (accessible, robust)
getByLabelText  → form elements
getByText       → visible text
getByTestId     → last resort only
```

## Mocking

```typescript
// Mock fetch
vi.spyOn(global, 'fetch').mockResolvedValue({
  json: () => Promise.resolve({ id: '1', name: 'Test' })
} as Response)

// Mock module
vi.mock('@/lib/api', () => ({
  fetchEntity: vi.fn(() => Promise.resolve({ id: '1' }))
}))
```

## Forbidden Patterns

| Forbidden | Use Instead |
|---|---|
| Shallow rendering | Full render with RTL |
| Querying by CSS class | Query by role/label/text |
| Testing internal state | Test observable output |
| Exact snapshot on complex components | Targeted assertions |
| Separate unit test for sub-component already covered | Integration test covers it |

## File Co-location

```
src/components/common/FormattedDescription.tsx
src/components/common/__tests__/FormattedDescription.test.tsx
```
