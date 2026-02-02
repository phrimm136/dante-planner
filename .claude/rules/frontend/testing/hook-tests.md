---
paths:
  - "frontend/**/*.test.ts"
  - "frontend/**/*.spec.ts"
---

# Hook Testing Patterns

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

## Test Output Analysis

```bash
# Run once, save to /tmp
yarn test > /tmp/fe-test-output.txt 2>&1

# Analyze from file (NOT by rerunning)
grep "FAIL\|ERROR" /tmp/fe-test-output.txt | tail -50
```
