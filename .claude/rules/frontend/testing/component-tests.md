---
paths:
  - "frontend/**/*.test.tsx"
  - "frontend/**/*.spec.tsx"
---

# Component Testing Patterns

## Mandatory Rules

- **Test behavior, not implementation** - User interactions, not internal state
- **Use `screen` queries** - Not container queries
- **Wrap state updates** - Use RTL's async utilities

## Template

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
