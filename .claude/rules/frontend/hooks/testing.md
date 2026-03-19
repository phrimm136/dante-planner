---
paths:
  - "frontend/src/hooks/**/__tests__/**"
---

# Hook Testing Patterns

## When to Test Hooks Directly

- Complex logic, async behavior, or context dependencies
- If an integration test already covers the hook's behavior via a component, skip the separate hook test

## Template

```typescript
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

function createWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  })
  return ({ children }) => (
    <QueryClientProvider client={client}>
      {children}
    </QueryClientProvider>
  )
}

describe('useEntityData', () => {
  it('returns entity data', async () => {
    const { result } = renderHook(() => useEntityData('123'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.data).toBeDefined())
    expect(result.current.data.id).toBe('123')
  })
})
```

## Hooks Needing Multiple Providers

```typescript
function createWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  })
  return ({ children }) => (
    <QueryClientProvider client={client}>
      <ThemeProvider>
        {children}
      </ThemeProvider>
    </QueryClientProvider>
  )
}
```

## Testing State Updates

```typescript
it('updates config on patch', async () => {
  const { result } = renderHook(() => usePlannerConfig(), {
    wrapper: createWrapper(),
  })

  act(() => {
    result.current.updateConfig({ theme: 'dark' })
  })

  expect(result.current.config.theme).toBe('dark')
})
```
