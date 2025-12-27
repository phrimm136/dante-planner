# Testing Guide

Comprehensive testing patterns using Vitest and React Testing Library. Covers unit tests, component tests, hook tests, and integration tests with production-level code quality.

---

## Testing Stack

| Tool | Purpose |
|------|---------|
| Vitest | Test runner (Vite-native, fast) |
| React Testing Library | Component testing (user-centric) |
| @testing-library/user-event | User interaction simulation |
| happy-dom | Fast DOM implementation |

---

## Test File Organization

```
src/
  components/
    IdentityCard/
      IdentityCard.tsx
      IdentityCard.test.tsx      # Co-located test
  hooks/
    useIdentityData.ts
    __tests__/
      useIdentityData.test.ts    # Tests in __tests__ folder
  lib/
    utils.ts
    utils.test.ts                # Co-located test
```

### Naming Conventions

```typescript
// Component tests
ComponentName.test.tsx

// Hook tests
useHookName.test.ts

// Utility tests
utilityName.test.ts

// Integration tests
featureName.integration.test.tsx
```

---

## Vitest Configuration

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'src/test/',
        '**/*.d.ts',
        'src/routeTree.gen.ts',
      ],
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@static': path.resolve(__dirname, '../static'),
    },
  },
})
```

### Test Setup File

```typescript
// src/test/setup.ts
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Cleanup after each test
afterEach(() => {
  cleanup()
})

// Mock matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// Mock ResizeObserver
global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}))
```

---

## Component Testing

### Basic Component Test

```typescript
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { IdentityCard } from './IdentityCard'
import type { Identity } from '@/types/IdentityTypes'

const mockIdentity: Identity = {
  id: 10101,
  sinnerId: 1,
  rarity: 3,
  HP: 150,
  minSpeed: 3,
  maxSpeed: 7,
  defenseLevel: 40,
}

describe('IdentityCard', () => {
  it('renders identity name', () => {
    render(<IdentityCard identity={mockIdentity} name="Test Identity" />)

    expect(screen.getByText('Test Identity')).toBeInTheDocument()
  })

  it('displays rarity correctly', () => {
    render(<IdentityCard identity={mockIdentity} name="Test Identity" />)

    expect(screen.getByText('OOO')).toBeInTheDocument() // Rarity 3 = OOO
  })

  it('applies selected styling when isSelected is true', () => {
    render(
      <IdentityCard
        identity={mockIdentity}
        name="Test Identity"
        isSelected={true}
      />
    )

    const card = screen.getByRole('button')
    expect(card).toHaveClass('ring-2')
  })
})
```

### Testing User Interactions

```typescript
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { IdentityCard } from './IdentityCard'

describe('IdentityCard interactions', () => {
  it('calls onSelect when clicked', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()

    render(
      <IdentityCard
        identity={mockIdentity}
        name="Test Identity"
        onSelect={onSelect}
      />
    )

    await user.click(screen.getByRole('button'))

    expect(onSelect).toHaveBeenCalledOnce()
    expect(onSelect).toHaveBeenCalledWith(mockIdentity)
  })

  it('shows tooltip on hover', async () => {
    const user = userEvent.setup()

    render(<IdentityCard identity={mockIdentity} name="Test Identity" />)

    await user.hover(screen.getByRole('button'))

    expect(await screen.findByRole('tooltip')).toBeInTheDocument()
  })

  it('handles keyboard navigation', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()

    render(
      <IdentityCard
        identity={mockIdentity}
        name="Test Identity"
        onSelect={onSelect}
      />
    )

    await user.tab()
    expect(screen.getByRole('button')).toHaveFocus()

    await user.keyboard('{Enter}')
    expect(onSelect).toHaveBeenCalledOnce()
  })
})
```

### Testing with Providers

```typescript
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/test/i18nForTests'
import { IdentityList } from './IdentityList'

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  })
}

interface WrapperProps {
  children: React.ReactNode
}

function createWrapper() {
  const queryClient = createTestQueryClient()

  return function Wrapper({ children }: WrapperProps) {
    return (
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>
          {children}
        </I18nextProvider>
      </QueryClientProvider>
    )
  }
}

describe('IdentityList', () => {
  it('renders loading state initially', () => {
    render(<IdentityList />, { wrapper: createWrapper() })

    expect(screen.getByRole('status')).toBeInTheDocument()
  })
})
```

---

## Hook Testing

### Testing Custom Hooks

```typescript
import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useIdentityData } from './useIdentityData'

// Mock fetch
const mockIdentityData = {
  id: 10101,
  sinnerId: 1,
  rarity: 3,
}

vi.mock('@/lib/fetcher', () => ({
  fetchJSON: vi.fn().mockResolvedValue(mockIdentityData),
}))

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  })

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    )
  }
}

describe('useIdentityData', () => {
  it('fetches and returns identity data', async () => {
    const { result } = renderHook(() => useIdentityData('10101'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.data).toBeDefined()
    })

    expect(result.current.data).toEqual(mockIdentityData)
  })

  it('handles loading state', () => {
    const { result } = renderHook(() => useIdentityData('10101'), {
      wrapper: createWrapper(),
    })

    expect(result.current.isLoading).toBe(true)
  })
})
```

### Testing Hooks with State Changes

```typescript
import { describe, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useFilters } from './useFilters'

describe('useFilters', () => {
  it('initializes with default filters', () => {
    const { result } = renderHook(() => useFilters())

    expect(result.current.filters).toEqual({
      sinner: null,
      rarity: null,
      affinity: null,
    })
  })

  it('updates single filter', () => {
    const { result } = renderHook(() => useFilters())

    act(() => {
      result.current.setFilter('sinner', 'yi-sang')
    })

    expect(result.current.filters.sinner).toBe('yi-sang')
  })

  it('resets all filters', () => {
    const { result } = renderHook(() => useFilters())

    act(() => {
      result.current.setFilter('sinner', 'yi-sang')
      result.current.setFilter('rarity', 3)
    })

    act(() => {
      result.current.resetFilters()
    })

    expect(result.current.filters).toEqual({
      sinner: null,
      rarity: null,
      affinity: null,
    })
  })
})
```

---

## Mocking

### Mocking Modules

```typescript
import { vi, describe, it, expect } from 'vitest'

// Mock entire module
vi.mock('@/lib/assetPaths', () => ({
  getIdentityImagePath: vi.fn((id: number) => `/mock/identity/${id}.png`),
  getEGOImagePath: vi.fn((id: number) => `/mock/ego/${id}.png`),
}))

// Mock specific function
import { getIdentityImagePath } from '@/lib/assetPaths'

describe('Component using asset paths', () => {
  it('uses mocked image path', () => {
    render(<IdentityCard identity={mockIdentity} name="Test" />)

    const image = screen.getByRole('img')
    expect(image).toHaveAttribute('src', '/mock/identity/10101.png')
    expect(getIdentityImagePath).toHaveBeenCalledWith(10101)
  })
})
```

### Mocking API Responses

```typescript
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'

// Mock fetch globally
const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => {
  mockFetch.mockReset()
})

describe('DataComponent', () => {
  it('handles successful API response', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ id: 1, name: 'Test' }),
    })

    render(<DataComponent />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Test')).toBeInTheDocument()
    })
  })

  it('handles API error', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Network error'))

    render(<DataComponent />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText(/error/i)).toBeInTheDocument()
    })
  })
})
```

### Mocking sonner Toast

```typescript
import { vi, describe, it, expect } from 'vitest'
import { toast } from 'sonner'

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  },
}))

describe('Form submission', () => {
  it('shows success toast on submit', async () => {
    const user = userEvent.setup()

    render(<MyForm />, { wrapper: createWrapper() })

    await user.click(screen.getByRole('button', { name: /submit/i }))

    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith('Form submitted successfully')
    })
  })
})
```

---

## Testing Async Components

### Testing with Suspense

```typescript
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Suspense } from 'react'
import { ErrorBoundary } from 'react-error-boundary'
import { IdentityDetail } from './IdentityDetail'

// Mock the hook to control loading state
vi.mock('@/hooks/useIdentityData', () => ({
  useIdentityData: vi.fn(),
}))

import { useIdentityData } from '@/hooks/useIdentityData'
const mockUseIdentityData = vi.mocked(useIdentityData)

describe('IdentityDetail', () => {
  it('renders loading state', () => {
    // Simulate suspense by throwing a promise
    mockUseIdentityData.mockImplementation(() => {
      throw new Promise(() => {})
    })

    render(
      <Suspense fallback={<div data-testid="loading">Loading...</div>}>
        <IdentityDetail identityId="10101" />
      </Suspense>,
      { wrapper: createWrapper() }
    )

    expect(screen.getByTestId('loading')).toBeInTheDocument()
  })

  it('renders data when loaded', () => {
    mockUseIdentityData.mockReturnValue({
      data: mockIdentity,
      i18n: { name: 'Test Identity' },
    })

    render(
      <Suspense fallback={<div>Loading...</div>}>
        <IdentityDetail identityId="10101" />
      </Suspense>,
      { wrapper: createWrapper() }
    )

    expect(screen.getByText('Test Identity')).toBeInTheDocument()
  })

  it('renders error state', () => {
    mockUseIdentityData.mockImplementation(() => {
      throw new Error('Failed to load')
    })

    render(
      <ErrorBoundary fallback={<div>Error occurred</div>}>
        <Suspense fallback={<div>Loading...</div>}>
          <IdentityDetail identityId="10101" />
        </Suspense>
      </ErrorBoundary>,
      { wrapper: createWrapper() }
    )

    expect(screen.getByText('Error occurred')).toBeInTheDocument()
  })
})
```

---

## Form Testing

### Testing react-hook-form

```typescript
import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CreateTeamForm } from './CreateTeamForm'

describe('CreateTeamForm', () => {
  it('validates required fields', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()

    render(<CreateTeamForm onSubmit={onSubmit} />)

    // Submit without filling form
    await user.click(screen.getByRole('button', { name: /create/i }))

    expect(await screen.findByText(/name is required/i)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits valid form data', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()

    render(<CreateTeamForm onSubmit={onSubmit} />)

    await user.type(screen.getByLabelText(/team name/i), 'My Team')
    await user.click(screen.getByRole('button', { name: /create/i }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({
        name: 'My Team',
      })
    })
  })

  it('shows loading state during submission', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn(() => new Promise((r) => setTimeout(r, 100)))

    render(<CreateTeamForm onSubmit={onSubmit} />)

    await user.type(screen.getByLabelText(/team name/i), 'My Team')
    await user.click(screen.getByRole('button', { name: /create/i }))

    expect(screen.getByRole('button', { name: /creating/i })).toBeDisabled()
  })
})
```

---

## Integration Testing

### Testing Feature Flows

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { IdentityPage } from '@/routes/IdentityPage'

describe('Identity Page Integration', () => {
  beforeEach(() => {
    // Setup mocks for full page test
    vi.mocked(fetchIdentityList).mockResolvedValue(mockIdentityList)
    vi.mocked(fetchI18n).mockResolvedValue(mockI18nData)
  })

  it('filters identities by sinner', async () => {
    const user = userEvent.setup()

    render(<IdentityPage />, { wrapper: createAppWrapper() })

    // Wait for data to load
    await waitFor(() => {
      expect(screen.getByText('Yi Sang')).toBeInTheDocument()
    })

    // Open filter dropdown
    await user.click(screen.getByRole('button', { name: /sinner/i }))

    // Select sinner
    await user.click(screen.getByRole('option', { name: /faust/i }))

    // Verify filtered results
    await waitFor(() => {
      const cards = screen.getAllByTestId('identity-card')
      cards.forEach((card) => {
        expect(within(card).getByText(/faust/i)).toBeInTheDocument()
      })
    })
  })

  it('navigates to identity detail on card click', async () => {
    const user = userEvent.setup()

    render(<IdentityPage />, { wrapper: createAppWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Yi Sang')).toBeInTheDocument()
    })

    await user.click(screen.getByTestId('identity-card-10101'))

    expect(screen.getByRole('heading', { name: /identity detail/i })).toBeInTheDocument()
  })
})
```

---

## Test Utilities

### Custom Render Function

```typescript
// src/test/utils.tsx
import { render, RenderOptions } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import { RouterProvider, createMemoryRouter } from '@tanstack/react-router'
import i18n from './i18nForTests'

interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  route?: string
  queryClient?: QueryClient
}

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })
}

export function renderWithProviders(
  ui: React.ReactElement,
  options: CustomRenderOptions = {}
) {
  const { queryClient = createTestQueryClient(), ...renderOptions } = options

  function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>
          {children}
        </I18nextProvider>
      </QueryClientProvider>
    )
  }

  return {
    ...render(ui, { wrapper: Wrapper, ...renderOptions }),
    queryClient,
  }
}

export * from '@testing-library/react'
export { renderWithProviders as render }
```

### Test Data Factories

```typescript
// src/test/factories.ts
import type { Identity } from '@/types/IdentityTypes'
import type { EGOGift } from '@/types/EGOGiftTypes'

export function createMockIdentity(overrides?: Partial<Identity>): Identity {
  return {
    id: 10101,
    sinnerId: 1,
    rarity: 3,
    HP: 150,
    minSpeed: 3,
    maxSpeed: 7,
    defenseLevel: 40,
    ...overrides,
  }
}

export function createMockEGOGift(overrides?: Partial<EGOGift>): EGOGift {
  return {
    id: 9001,
    tier: 1,
    keyword: 'test',
    ...overrides,
  }
}

export function createMockIdentityList(count: number = 5): Identity[] {
  return Array.from({ length: count }, (_, i) =>
    createMockIdentity({
      id: 10101 + i,
      sinnerId: (i % 12) + 1,
    })
  )
}
```

### i18n Test Setup

```typescript
// src/test/i18nForTests.ts
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

i18n.use(initReactI18next).init({
  lng: 'en',
  fallbackLng: 'en',
  ns: ['common'],
  defaultNS: 'common',
  resources: {
    en: {
      common: {
        loading: 'Loading...',
        error: 'An error occurred',
        submit: 'Submit',
        cancel: 'Cancel',
      },
    },
  },
  interpolation: {
    escapeValue: false,
  },
})

export default i18n
```

---

## Testing Best Practices

### Query Best Practices

```typescript
// ✅ CORRECT - Query by role (accessible)
screen.getByRole('button', { name: /submit/i })
screen.getByRole('heading', { level: 1 })
screen.getByRole('textbox', { name: /email/i })

// ✅ CORRECT - Query by label (accessible)
screen.getByLabelText(/email address/i)

// ✅ CORRECT - Query by text for content
screen.getByText(/welcome back/i)

// ⚠️ OK - Use testid for complex elements without accessible name
screen.getByTestId('identity-card-10101')

// ❌ AVOID - Querying by class or implementation details
container.querySelector('.btn-primary')
screen.getByClassName('identity-card')
```

### Async Best Practices

```typescript
// ✅ CORRECT - Use findBy for async elements
const button = await screen.findByRole('button', { name: /submit/i })

// ✅ CORRECT - Use waitFor for async assertions
await waitFor(() => {
  expect(screen.getByText('Success')).toBeInTheDocument()
})

// ✅ CORRECT - Use waitForElementToBeRemoved
await waitForElementToBeRemoved(() => screen.queryByText('Loading...'))

// ❌ AVOID - Using sleep/setTimeout
await new Promise((r) => setTimeout(r, 1000))
```

### User Event Best Practices

```typescript
// ✅ CORRECT - Setup userEvent once
const user = userEvent.setup()
await user.click(button)
await user.type(input, 'text')

// ✅ CORRECT - Simulate realistic user behavior
await user.type(input, 'hello')  // Types character by character

// ❌ AVOID - fireEvent for user interactions (less realistic)
fireEvent.click(button)
fireEvent.change(input, { target: { value: 'hello' } })
```

---

## Running Tests

```bash
# Run all tests
yarn test

# Run tests in watch mode
yarn test:watch

# Run with UI
yarn test:ui

# Run with coverage
yarn test:coverage

# Run specific file
yarn test IdentityCard

# Run tests matching pattern
yarn test -t "filters identities"
```

---

## Summary

| Aspect | Approach |
|--------|----------|
| Test runner | Vitest |
| Component testing | React Testing Library |
| User interactions | @testing-library/user-event |
| DOM environment | happy-dom |
| Queries | Prefer role/label over testid |
| Async | findBy, waitFor, waitForElementToBeRemoved |
| Mocking | vi.mock, vi.fn |
| Factories | Create reusable test data builders |

**See Also:**
- [component-patterns.md](component-patterns.md) - Component structure
- [data-fetching.md](data-fetching.md) - Hook patterns to test
- [common-patterns.md](common-patterns.md) - Form patterns to test
