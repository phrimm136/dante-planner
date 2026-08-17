# Research: Vitest Configuration for React 19 + Vite 7 + TanStack Stack

## Overview of Codebase

### Current Stack Analysis

The LimbusPlanner frontend is built on a modern stack:
- **React 19.2.0**: Latest React version with improved performance and new features
- **Vite 7.2.2**: Latest Vite with faster build times
- **TypeScript 5.9.3**: Provides type safety across the application
- **TanStack Query v5.90.8**: Data fetching and caching layer
- **TanStack Router v1.135.2**: Type-safe routing with code-based configuration
- **shadcn/ui**: Component library built on Radix UI primitives
- **Tailwind CSS**: Utility-first CSS framework

### Testing Requirements

Based on the current setup, testing infrastructure needs to:
1. Support React 19 (requires Testing Library v16.1.0+)
2. Work with Vite 7's configuration system
3. Handle TanStack Query providers in tests
4. Support TanStack Router with memory history for navigation tests
5. Resolve TypeScript path aliases (@/ imports)
6. Mock API requests for query testing
7. Provide coverage reporting
8. Integrate with existing dev workflow

## Compatibility Matrix

### Vitest Versions

**Latest Stable**: Vitest v4.0.8 (released November 7, 2025)

**Version Compatibility**:
- Vitest 3.2+ officially supports Vite 7
- Vitest 4.0.8 is the recommended version for this stack
- Node.js 20.19+ or 22.12+ required (Vite 7 requirement)

### Testing Library Versions

**Critical for React 19 Support**:
- `@testing-library/react` v16.1.0+ (v13.x only supports React 18)
- `@testing-library/user-event` v14.6.1 (latest stable)
- **Do NOT install** `@testing-library/jest-dom` (use Vitest's built-in matchers)

### Dependencies Summary

```json
{
  "devDependencies": {
    "vitest": "^4.0.8",
    "@vitest/ui": "^4.0.8",
    "@vitest/coverage-v8": "^4.0.8",
    "@testing-library/react": "^16.1.0",
    "@testing-library/user-event": "^14.6.1",
    "happy-dom": "^15.11.7",
    "msw": "^2.7.0"
  }
}
```

## Codebase Structure

### Current Frontend Organization

```
frontend/
├── src/
│   ├── components/
│   │   └── ui/          # shadcn/ui components (Button, etc.)
│   ├── hooks/
│   │   └── useExampleQuery.ts  # Custom query hooks
│   ├── routes/
│   │   ├── __root.tsx   # Minimal root for plugin
│   │   ├── HomePage.tsx # Home page component
│   │   └── AboutPage.tsx # About page component
│   ├── lib/
│   │   ├── queryClient.ts  # QueryClient singleton
│   │   ├── router.tsx      # Router configuration
│   │   └── utils.ts        # cn() helper
│   ├── main.tsx         # App entry point
│   └── index.css        # Global styles
├── vite.config.ts       # Vite configuration (with plugins)
├── tsconfig.json        # Base TypeScript config
├── tsconfig.app.json    # App-specific TS config
└── package.json
```

### Where Tests Should Live

**Co-location Strategy** (Recommended):
```
frontend/src/
├── components/ui/
│   ├── button.tsx
│   └── button.test.tsx       # ✅ Test next to component
├── hooks/
│   ├── useExampleQuery.ts
│   └── useExampleQuery.test.ts  # ✅ Test next to hook
└── routes/
    ├── HomePage.tsx
    └── HomePage.test.tsx     # ✅ Test next to route
```

**Benefits**:
- Easy to find related tests
- Tests move/delete with code
- Clear which code has tests
- Common in modern React projects

### Test Infrastructure to Add

```
frontend/
├── src/
│   ├── mocks/
│   │   ├── handlers.ts     # MSW API mock handlers
│   │   └── server.ts       # MSW server setup
│   └── test-utils/
│       ├── queryClient.ts  # Test QueryClient factory
│       ├── router.tsx      # Test router helpers
│       └── renderWithProviders.tsx  # Custom render
└── vitest.setup.ts         # Global test setup
```

## Configuration Patterns

### Vitest Integration with Vite Config

The existing `vite.config.ts` needs extension for test configuration:

```typescript
/// <reference types="vitest" />
import { defineConfig } from 'vitest/config'
// ... existing imports

export default defineConfig({
  // ... existing plugins and resolve config

  test: {
    environment: 'happy-dom',
    globals: true,
    setupFiles: './vitest.setup.ts',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.test.{ts,tsx}',
        'src/main.tsx',
        'src/routeTree.gen.ts',
      ],
    },
    pool: 'forks',
    clearMocks: true,
    restoreMocks: true,
  },
})
```

**Key Configuration Decisions**:

1. **Test Environment**: `happy-dom` vs `jsdom`
   - **happy-dom**: Faster, lower memory, good for standard React testing
   - **jsdom**: More comprehensive browser API emulation
   - **Recommendation**: Start with happy-dom, switch if you hit missing APIs

2. **Global Test APIs**: `globals: true`
   - Enables `describe`, `it`, `expect` without imports
   - Matches Jest/Vitest defaults
   - Cleaner test files

3. **Coverage Provider**: `v8`
   - Faster than Istanbul (no transpilation)
   - Lower memory usage
   - Default in Vitest
   - Accuracy improved significantly in recent versions

4. **Process Pool**: `forks` vs `threads`
   - `forks`: More stable, better isolation
   - `threads`: Faster startup, shared memory
   - **Recommendation**: Start with forks for stability

### TypeScript Configuration

Add Vitest types to `tsconfig.app.json`:

```json
{
  "compilerOptions": {
    "types": ["vitest/globals"]
  }
}
```

This enables type inference for `describe`, `it`, `expect` without imports.

### Path Alias Resolution

The existing `@/` alias in `vite.config.ts` automatically works in tests:

```typescript
resolve: {
  alias: {
    '@': path.resolve(__dirname, './src'),
  },
}
```

Tests can use `import { Button } from '@/components/ui/button'` just like app code.

## TanStack Integration Patterns

### TanStack Query Testing Strategy

**Key Principles**:
1. **Create test QueryClient per test** - Ensures isolation
2. **Disable retries** - Prevents timeout issues
3. **Set gcTime to Infinity** - Avoids "tests didn't exit" errors
4. **Mock network, not the library** - Use MSW for realistic testing

**Test QueryClient Factory**:

```typescript
// src/test-utils/queryClient.ts
import { QueryClient } from '@tanstack/react-query'

export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,      // Don't retry failed queries in tests
        gcTime: Infinity,  // Prevent premature cleanup
        staleTime: 0,      // Always consider data stale
      },
      mutations: {
        retry: false,
      },
    },
  })
}
```

**Custom Render with Query Provider**:

```typescript
// src/test-utils/render.tsx
export function renderWithQuery(ui: ReactElement, options = {}) {
  const testQueryClient = options.queryClient ?? createTestQueryClient()

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={testQueryClient}>
        {children}
      </QueryClientProvider>
    )
  }

  return {
    ...render(ui, { wrapper: Wrapper, ...options }),
    queryClient: testQueryClient,
  }
}
```

### TanStack Router Testing Strategy

**Key Principles**:
1. **Use createMemoryHistory** - Controlled navigation in tests
2. **Set defaultPendingMs to 0** - Prevents slow tests (critical!)
3. **Create test router per test** - Ensures isolation
4. **No official testing utilities** - Use custom helpers

**Test Router Factory**:

```typescript
// src/test-utils/router.tsx
export function createTestRouter(options = {}) {
  const rootRoute = createRootRoute({
    component: options.component || (() => <div />),
  })

  const testRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/',
    component: options.component || (() => <div />),
  })

  const routeTree = rootRoute.addChildren([testRoute])

  return createRouter({
    routeTree,
    history: createMemoryHistory({
      initialEntries: options.initialEntries || ['/'],
    }),
    defaultPendingMs: 0,  // CRITICAL: Prevents slow tests
  })
}
```

**Why defaultPendingMs: 0 Matters**:
- Default is 1000ms (1 second wait before showing pending UI)
- Tests will wait this duration unnecessarily
- Can make test suite hundreds of ms slower per test
- Set to 0 for immediate navigation in tests

### Combined Providers Testing

**For components using both Query and Router**:

```typescript
// src/test-utils/renderWithProviders.tsx
export function renderWithProviders(ui: ReactElement, options = {}) {
  const testQueryClient = options.queryClient ?? createTestQueryClient()
  const testRouter = options.router ?? createTestRouter({
    initialEntries: [options.initialRoute || '/']
  })

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={testQueryClient}>
        <RouterProvider router={testRouter}>
          {children}
        </RouterProvider>
      </QueryClientProvider>
    )
  }

  return {
    ...render(ui, { wrapper: Wrapper, ...options }),
    queryClient: testQueryClient,
    router: testRouter,
  }
}
```

## MSW (Mock Service Worker) Integration

### Why MSW Over Mocking Libraries

**Advantages**:
- Intercepts requests at network level (more realistic)
- Same mocks work in browser and Node.js
- Doesn't require mocking individual fetch/axios calls
- Better developer experience with type-safe handlers
- Works with any data fetching library

### MSW Setup Pattern

**Handlers Definition**:

```typescript
// src/mocks/handlers.ts
import { http, HttpResponse } from 'msw'

export const handlers = [
  http.get('/api/example', () => {
    return HttpResponse.json({
      message: 'Mocked response',
      timestamp: Date.now(),
    })
  }),

  http.post('/api/data', async ({ request }) => {
    const body = await request.json()
    return HttpResponse.json({ success: true, data: body })
  }),
]
```

**Server Setup**:

```typescript
// src/mocks/server.ts
import { setupServer } from 'msw/node'
import { handlers } from './handlers'

export const server = setupServer(...handlers)
```

**Integration in vitest.setup.ts**:

```typescript
import { beforeAll, afterEach, afterAll } from 'vitest'
import { server } from './src/mocks/server'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
```

**Per-Test Handler Override**:

```typescript
it('handles error response', async () => {
  server.use(
    http.get('/api/example', () => {
      return new HttpResponse(null, { status: 500 })
    })
  )

  // Test error handling...
})
```

## Example Test Patterns

### Component Test (shadcn/ui Button)

```typescript
// src/components/ui/button.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { Button } from './button'

describe('Button', () => {
  it('renders with text', () => {
    render(<Button>Click me</Button>)
    expect(screen.getByRole('button', { name: /click me/i }))
      .toBeInTheDocument()
  })

  it('handles click events', async () => {
    const user = userEvent.setup()
    const handleClick = vi.fn()

    render(<Button onClick={handleClick}>Click</Button>)
    await user.click(screen.getByRole('button'))

    expect(handleClick).toHaveBeenCalledOnce()
  })

  it('applies variant styles', () => {
    render(<Button variant="destructive">Delete</Button>)
    expect(screen.getByRole('button')).toHaveClass('destructive')
  })

  it('can be disabled', () => {
    render(<Button disabled>Disabled</Button>)
    expect(screen.getByRole('button')).toBeDisabled()
  })
})
```

### Query Hook Test

```typescript
// src/hooks/useExampleQuery.test.ts
import { describe, it, expect } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { useExampleQuery } from './useExampleQuery'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { QueryClientProvider } from '@tanstack/react-query'

describe('useExampleQuery', () => {
  it('fetches data successfully', async () => {
    server.use(
      http.get('/api/example', () => {
        return HttpResponse.json({
          message: 'Test data',
          timestamp: 1234567890,
        })
      })
    )

    const queryClient = createTestQueryClient()
    const wrapper = ({ children }) => (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    )

    const { result } = renderHook(() => useExampleQuery(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.message).toBe('Test data')
  })

  it('handles errors', async () => {
    server.use(
      http.get('/api/example', () => {
        return new HttpResponse(null, { status: 500 })
      })
    )

    const queryClient = createTestQueryClient()
    const wrapper = ({ children }) => (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    )

    const { result } = renderHook(() => useExampleQuery(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
```

### Route Component Test

```typescript
// src/routes/HomePage.test.tsx
import { describe, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import HomePage from './HomePage'

describe('HomePage', () => {
  it('renders page title', () => {
    renderWithProviders(<HomePage />)
    expect(screen.getByText(/limbus planner/i)).toBeInTheDocument()
  })

  it('fetches and displays query data', async () => {
    renderWithProviders(<HomePage />)

    // Wait for loading to finish
    await waitFor(() => {
      expect(screen.queryByText(/loading/i)).not.toBeInTheDocument()
    })

    // Data should be displayed
    expect(screen.getByText(/hello from tanstack query/i))
      .toBeInTheDocument()
  })

  it('navigates to about page', async () => {
    const user = userEvent.setup()
    const { router } = renderWithProviders(<HomePage />)

    await user.click(screen.getByRole('link', { name: /about/i }))

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/about')
    })
  })
})
```

## Gotchas and Pitfalls

### React 19 Specific Issues

#### 1. Snapshot Testing with React 19

**Issue**: `@vitest/pretty-format` bundles React 18's `react-is`, causing incorrect snapshot formatting for React 19 elements.

**Impact**: Snapshots of React components may not format correctly.

**Workaround**:
- Avoid snapshot testing React components
- Use explicit assertions instead: `expect(element).toHaveTextContent(...)`
- Wait for Vitest fix (tracked in issue #6908)

#### 2. Testing Library Version Requirement

**Issue**: `@testing-library/react` v13.x and below only support React 18.

**Solution**: MUST use v16.1.0+ for React 19 support.

**Symptom if wrong version**: Type errors or runtime errors about React version mismatch.

#### 3. Suspense Behavior Changes

**Issue**: React 19 changed how Suspense boundary errors propagate.

**Impact**: Tests using Suspense may need updates.

**Solution**: Use error boundaries and test error states explicitly.

### Vite 7 Specific Issues

#### 1. Node.js Version Requirement

**Issue**: Vite 7 requires Node.js 20.19+ or 22.12+ (Node 18 EOL).

**Impact**: Tests won't run on older Node versions.

**Solution**: Ensure CI/CD uses Node 20+.

#### 2. Plugin Order Still Matters

**Issue**: TanStack Router plugin must come before React plugin (same as dev).

**Impact**: Type generation fails if order is wrong.

**Solution**: Keep plugin order consistent between dev and test configs.

### TanStack Query Testing Issues

#### 1. Tests Not Exiting

**Issue**: Tests hang and don't exit after completion.

**Cause**: QueryClient garbage collection timers still active.

**Solution**: Set `gcTime: Infinity` in test QueryClient.

```typescript
// ✅ Correct
queries: {
  gcTime: Infinity,  // Prevents premature cleanup
}

// ❌ Wrong
queries: {
  gcTime: 5 * 60 * 1000,  // Tests may hang
}
```

#### 2. Flaky Tests from Retries

**Issue**: Tests randomly fail due to retry logic.

**Cause**: Failed queries retry by default, causing timing issues.

**Solution**: Disable retries in test QueryClient.

```typescript
// ✅ Correct
queries: {
  retry: false,  // No retries in tests
}

// ❌ Wrong
queries: {
  retry: 1,  // Tests become flaky
}
```

#### 3. Mocking useQuery Directly (Anti-pattern)

**Issue**: Mocking `useQuery` with `vi.mock()` breaks test isolation.

**Cause**: Mock affects all tests, hard to override per-test.

**Solution**: Mock network requests with MSW instead.

```typescript
// ❌ Don't do this
vi.mock('@tanstack/react-query', () => ({
  useQuery: () => ({ data: 'mocked' }),
}))

// ✅ Do this instead
server.use(
  http.get('/api/endpoint', () => {
    return HttpResponse.json({ data: 'mocked' })
  })
)
```

### TanStack Router Testing Issues

#### 1. Slow Tests Without defaultPendingMs: 0

**Issue**: Tests take hundreds of ms longer than necessary.

**Cause**: Router waits 1000ms before showing pending state.

**Solution**: Set `defaultPendingMs: 0` in test routers.

```typescript
// ✅ Correct
createRouter({
  routeTree,
  history: createMemoryHistory(...),
  defaultPendingMs: 0,  // CRITICAL for fast tests
})

// ❌ Wrong (tests will be slow)
createRouter({
  routeTree,
  history: createMemoryHistory(...),
  // defaultPendingMs defaults to 1000
})
```

**Impact**: Without this, each navigation adds 1000ms to test time.

#### 2. No Official Testing Utilities

**Issue**: TanStack Router doesn't provide built-in test helpers.

**Cause**: Library is newer, testing utils not prioritized yet.

**Solution**: Create custom helpers (as shown in this research).

**Community Pattern**: Most projects create `test-utils/router.tsx` with custom helpers.

#### 3. Router Type Registration in Tests

**Issue**: TypeScript errors about router types in test files.

**Cause**: Type augmentation in `router.tsx` may not apply to tests.

**Solution**: Ensure `tsconfig.app.json` includes test files, or create separate `tsconfig.vitest.json`.

### Environment-Specific Issues

#### 1. happy-dom Missing APIs

**Issue**: Some browser APIs not implemented in happy-dom.

**Examples**:
- `window.getComputedStyle()` (limited support)
- `IntersectionObserver` (need to mock)
- `ResizeObserver` (need to mock)

**Solution**: Mock missing APIs in `vitest.setup.ts` or switch to jsdom.

```typescript
// Mock IntersectionObserver
global.IntersectionObserver = class IntersectionObserver {
  constructor() {}
  disconnect() {}
  observe() {}
  takeRecords() { return [] }
  unobserve() {}
}
```

#### 2. matchMedia Not Defined

**Issue**: Tests using responsive components fail with "matchMedia is not a function".

**Cause**: Neither happy-dom nor jsdom implement `window.matchMedia` fully.

**Solution**: Mock in `vitest.setup.ts`.

```typescript
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
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
```

### MSW Version Issues

#### 1. MSW v2 Breaking Changes

**Issue**: MSW v2 has different API than v1.

**Breaking Changes**:
- `rest.get()` → `http.get()`
- `res(ctx.json())` → `HttpResponse.json()`
- Setup function changes

**Solution**: Use MSW v2.x (latest) and follow v2 API.

```typescript
// ✅ MSW v2 (correct)
import { http, HttpResponse } from 'msw'
http.get('/api/endpoint', () => {
  return HttpResponse.json({ data: 'value' })
})

// ❌ MSW v1 (outdated)
import { rest } from 'msw'
rest.get('/api/endpoint', (req, res, ctx) => {
  return res(ctx.json({ data: 'value' }))
})
```

### Coverage Issues

#### 1. Inflated Coverage from Generated Files

**Issue**: Coverage includes generated files like `routeTree.gen.ts`.

**Solution**: Exclude in coverage config.

```typescript
coverage: {
  exclude: [
    'src/routeTree.gen.ts',
    'src/**/*.d.ts',
    'src/main.tsx',
  ],
}
```

#### 2. Untested Code Not Shown

**Issue**: Coverage only shows tested files, missing untested files.

**Solution**: Use `coverage.all: true` to include all files.

```typescript
coverage: {
  all: true,
  include: ['src/**/*.{ts,tsx}'],
}
```

## Testing Workflow

### Scripts to Add

```json
{
  "scripts": {
    "test": "vitest",
    "test:ui": "vitest --ui",
    "test:coverage": "vitest run --coverage",
    "test:watch": "vitest watch"
  }
}
```

### Recommended Workflow

1. **Development**: `yarn test:watch` (or `yarn test`) - Auto-rerun on changes
2. **Pre-commit**: `yarn test run` - Run all tests once
3. **CI/CD**: `yarn test:coverage` - Run with coverage reporting
4. **Debugging**: `yarn test:ui` - Visual test explorer

### Watch Mode Features

Vitest watch mode provides:
- Automatic re-run on file changes
- Filtered test execution (by filename, test name)
- Coverage on demand
- Snapshot update mode
- Interactive CLI

**Common Commands in Watch Mode**:
- Press `a` - Run all tests
- Press `f` - Run only failed tests
- Press `t` - Filter by test name pattern
- Press `q` - Quit watch mode
- Press `c` - Clear console
- Press `u` - Update snapshots

## Performance Optimization

### Test Execution Speed

**Expected Performance**:
- Simple component test: 5-20ms
- Query hook test with MSW: 50-200ms
- Route navigation test: 20-100ms

**If tests are slow**:

1. **Check defaultPendingMs**: Must be 0 for router tests
2. **Check retry logic**: Should be disabled in test QueryClient
3. **Use happy-dom**: Faster than jsdom
4. **Use pool: 'forks'**: Better than threads for stability
5. **Limit coverage calculation**: Only run when needed

### Memory Management

**Tips for Large Test Suites**:
- Use `maxConcurrency` to limit parallel tests
- Set `gcTime: Infinity` to prevent cleanup issues
- Create fresh QueryClient per test (not shared)
- Clean up event listeners in afterEach
- Use `clearMocks: true` to reset mocks

## Summary

### Key Decisions for Implementation

1. **Vitest v4.0.8** - Latest stable, full Vite 7 support
2. **@testing-library/react v16.1.0+** - React 19 compatibility
3. **happy-dom** - Fast environment (fallback to jsdom if needed)
4. **v8 coverage** - Faster, lower memory
5. **MSW for API mocking** - Network-level interception
6. **Co-located tests** - Tests next to source files
7. **Custom render utilities** - For TanStack providers

### Implementation Checklist

- [ ] Install dependencies (vitest, testing-library, happy-dom, msw)
- [ ] Configure `vite.config.ts` with test options
- [ ] Create `vitest.setup.ts` with global setup
- [ ] Add TypeScript types to `tsconfig.app.json`
- [ ] Create test utilities (`queryClient`, `router`, `renderWithProviders`)
- [ ] Set up MSW handlers and server
- [ ] Add test scripts to `package.json`
- [ ] Write smoke test for App/HomePage
- [ ] Run tests and verify coverage reports

### Critical Configuration Values

```typescript
// Test QueryClient
queries: {
  retry: false,        // ← CRITICAL: Prevents flaky tests
  gcTime: Infinity,    // ← CRITICAL: Prevents hanging tests
}

// Test Router
createRouter({
  defaultPendingMs: 0, // ← CRITICAL: Prevents slow tests
})

// Vitest Config
test: {
  environment: 'happy-dom',  // Fast alternative to jsdom
  globals: true,             // Enable global test APIs
  setupFiles: './vitest.setup.ts',
}
```

### Next Steps After Research

1. Review this research document
2. Create implementation plan (`plan.md`)
3. Execute plan to configure Vitest
4. Write smoke test for existing components
5. Verify coverage reports work
6. Document implementation in `code.md`
