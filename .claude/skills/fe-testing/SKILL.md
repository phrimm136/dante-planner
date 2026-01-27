---
name: fe-testing
description: Frontend testing with Vitest and React Testing Library.
---

# Frontend Testing Patterns

## Rules

- **Test behavior, not implementation** - User interactions, not internal state
- **Use `screen` queries** - Not container queries
- **Wrap state updates** - Use RTL's async utilities
- **Mock at boundaries** - Network, not internal modules

## File Location

```
src/components/Card.tsx
src/components/Card.test.tsx   # Co-located
```

## Component Test Template

```typescript
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { Card } from './Card'

describe('Card', () => {
  it('renders item name', () => {
    render(<Card item={{ id: '1', name: 'Test' }} />)
    expect(screen.getByText('Test')).toBeInTheDocument()
  })

  it('calls onSelect when clicked', async () => {
    const onSelect = vi.fn()
    const user = userEvent.setup()
    render(<Card item={{ id: '1', name: 'Test' }} onSelect={onSelect} />)

    await user.click(screen.getByRole('button'))
    expect(onSelect).toHaveBeenCalledWith({ id: '1', name: 'Test' })
  })
})
```

## Hook Test Template

```typescript
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

function createWrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
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

## Test Output Analysis

**Run once, save to /tmp, then analyze:**

```bash
# Run once
yarn test > /tmp/fe-test-output.txt 2>&1 ; echo "Exit code: $?"

# Analyze from file (NOT by rerunning tests)
grep "FAIL\|ERROR" /tmp/fe-test-output.txt | tail -50
grep "Test Suites:" /tmp/fe-test-output.txt

# If file > 256KB, extract specific sections
grep -A 10 "FailingTest" /tmp/fe-test-output.txt > /tmp/fe-errors.txt
```

## Reference

- Run: `yarn test`
- Coverage: `yarn test --coverage`
- Why: `docs/learning/frontend-patterns.md`
