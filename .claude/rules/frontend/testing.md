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
- **Mock at the right boundary** — see the Hybrid Mocking Policy below

## Hybrid Mocking Policy

Choose the mock by the boundary the test crosses. Do not mock the network for a
test whose subject is pure state.

| Subject under test | Boundary | Mock with |
|---|---|---|
| Integration / component that hits the network | HTTP | MSW — register per-test handlers via `server.use(...)`; default handlers in `src/test-utils/handlers.ts` |
| Pure state / Zustand store | none | `renderHook`, no mocks — assert state transitions directly |
| Orchestration hook (composes adapters) | injected adapter | Fake the injected adapter (`vi.mock` the adapter module); do not mock `fetch` |

MSW lifecycle is scoped per network-level test file (`beforeAll(server.listen)`
/ `afterEach(server.resetHandlers)` / `afterAll(server.close)`), because
`vitest.setup.ts` installs a suite-wide `globalThis.fetch = vi.fn()` that
state/adapter tests rely on. A network test restores that mock in its own
`afterAll`. See `src/test-utils/mswServer.ts`.

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
