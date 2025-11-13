# Implementation Plan: Configure Vitest

## Task Overview

Set up Vitest testing framework for the LimbusPlanner frontend application (React 19 + Vite 7 + TypeScript + TanStack Query + TanStack Router). This includes:

- Installing Vitest v4 and related testing dependencies
- Configuring Vitest in the existing Vite configuration
- Setting up test environment with happy-dom
- Creating test utilities for TanStack Query and Router
- Setting up MSW (Mock Service Worker) for API mocking
- Writing smoke test for HomePage component
- Configuring coverage reporting
- Verifying all tests run successfully

**Target**: Production-ready testing infrastructure that supports the full stack (React 19, TanStack libraries, shadcn/ui components).

## Steps to Implementation

### Step 1: Install Vitest Dependencies (~2 minutes)

**What**: Install all required testing packages.

**Actions**:
```bash
cd /home/user/github/LimbusPlanner/frontend
yarn add -D vitest@^4.0.8 @vitest/ui@^4.0.8 @vitest/coverage-v8@^4.0.8
yarn add -D @testing-library/react@^16.1.0 @testing-library/user-event@^14.6.1
yarn add -D happy-dom@^15.11.7 msw@^2.7.0
```

**Dependencies**:
- `vitest` v4.0.8 - Test runner (Vite-native)
- `@vitest/ui` - Visual test explorer UI
- `@vitest/coverage-v8` - Coverage reporting with v8 provider
- `@testing-library/react` v16.1.0+ - React 19 compatible testing utilities
- `@testing-library/user-event` v14.6.1 - User interaction simulation
- `happy-dom` v15.11.7 - Fast DOM environment
- `msw` v2.7.0 - Network request mocking

**Why these versions**:
- Testing Library v16.1.0+ required for React 19 support
- Vitest v4.0.8 has full Vite 7 support
- happy-dom is faster than jsdom for standard React testing

### Step 2: Add Test Scripts to package.json (~1 minute)

**What**: Add npm scripts for running tests.

**Actions**:
Modify `frontend/package.json` to add:
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

**Scripts explanation**:
- `test` - Run tests in watch mode (auto-rerun on changes)
- `test:ui` - Open visual test explorer
- `test:coverage` - Run tests once with coverage report
- `test:watch` - Explicitly run in watch mode

### Step 3: Configure Vitest in vite.config.ts (~3 minutes)

**What**: Extend existing Vite configuration with test settings.

**Actions**:
Modify `frontend/vite.config.ts`:

1. Add type reference at the top: `/// <reference types="vitest" />`
2. Change import: `import { defineConfig } from 'vitest/config'`
3. Add `test` configuration object

**Configuration**:
```typescript
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
      'src/**/*.spec.{ts,tsx}',
      'src/main.tsx',
      'src/routeTree.gen.ts',
      'src/**/*.d.ts',
    ],
  },
  pool: 'forks',
  clearMocks: true,
  restoreMocks: true,
}
```

**Key settings**:
- `environment: 'happy-dom'` - Fast DOM emulation
- `globals: true` - Enable `describe`, `it`, `expect` without imports
- `setupFiles` - Global test setup file
- `coverage.provider: 'v8'` - Fast coverage with V8
- `pool: 'forks'` - Stable process isolation

### Step 4: Create Global Test Setup File (~4 minutes)

**What**: Create `vitest.setup.ts` with global test configuration.

**Actions**:
Create `frontend/vitest.setup.ts` with:

1. Import cleanup from Testing Library
2. Configure afterEach cleanup
3. Mock window.matchMedia (required by UI libraries)
4. Mock IntersectionObserver (required by some components)
5. Set up MSW server integration

**File structure**:
```typescript
import { expect, afterEach, beforeAll, afterAll, vi } from 'vitest'
import { cleanup } from '@testing-library/react'
import { server } from './src/mocks/server'

// Cleanup after each test
afterEach(() => {
  cleanup()
})

// MSW server lifecycle
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

// Mock window.matchMedia
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

// Mock IntersectionObserver
global.IntersectionObserver = class IntersectionObserver {
  constructor() {}
  disconnect() {}
  observe() {}
  takeRecords() { return [] }
  unobserve() {}
}
```

**Why each mock**:
- `matchMedia` - Used by responsive components, not in happy-dom
- `IntersectionObserver` - Used by lazy loading, not in happy-dom

### Step 5: Update TypeScript Configuration (~1 minute)

**What**: Add Vitest types to TypeScript configuration.

**Actions**:
Modify `frontend/tsconfig.app.json`:

Add to `compilerOptions.types`:
```json
{
  "compilerOptions": {
    "types": ["vite/client", "vitest/globals"]
  }
}
```

**Why**: Enables type inference for `describe`, `it`, `expect` without imports.

### Step 6: Create Test Utilities - QueryClient (~3 minutes)

**What**: Create test QueryClient factory for TanStack Query testing.

**Actions**:
Create `frontend/src/test-utils/queryClient.ts`:

```typescript
import { QueryClient } from '@tanstack/react-query'

/**
 * Creates a QueryClient configured for testing
 * - Disables retries to avoid test timeouts
 * - Sets gcTime to Infinity to prevent "tests didn't exit" errors
 * - Sets staleTime to 0 so data is always refetched in tests
 */
export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,      // Don't retry failed queries in tests
        gcTime: Infinity,  // Prevent premature garbage collection
        staleTime: 0,      // Always consider data stale in tests
      },
      mutations: {
        retry: false,      // Don't retry failed mutations in tests
      },
    },
  })
}
```

**Critical settings**:
- `retry: false` - Prevents flaky tests from retry logic
- `gcTime: Infinity` - Prevents "tests didn't exit" errors

### Step 7: Create Test Utilities - Router (~4 minutes)

**What**: Create test router factory for TanStack Router testing.

**Actions**:
Create `frontend/src/test-utils/router.tsx`:

```typescript
import { ReactNode } from 'react'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
} from '@tanstack/react-router'

interface CreateTestRouterOptions {
  initialEntries?: string[]
  component?: () => ReactNode
}

/**
 * Creates a router configured for testing
 * - Uses memory history (no real browser navigation)
 * - Sets defaultPendingMs to 0 to prevent slow tests
 */
export function createTestRouter({
  initialEntries = ['/'],
  component,
}: CreateTestRouterOptions = {}) {
  const rootRoute = createRootRoute({
    component: component || (() => <div data-testid="test-root" />),
  })

  const testRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/',
    component: component || (() => <div>Test Route</div>),
  })

  const routeTree = rootRoute.addChildren([testRoute])

  return createRouter({
    routeTree,
    history: createMemoryHistory({
      initialEntries,
    }),
    defaultPendingMs: 0,  // CRITICAL: Prevents 1000ms delay per test
  })
}
```

**Critical setting**:
- `defaultPendingMs: 0` - Without this, tests wait 1000ms per navigation!

### Step 8: Create Test Utilities - Combined Render (~5 minutes)

**What**: Create custom render function with both Query and Router providers.

**Actions**:
Create `frontend/src/test-utils/renderWithProviders.tsx`:

```typescript
import { ReactElement, ReactNode } from 'react'
import { render, RenderOptions } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { createTestQueryClient } from './queryClient'
import { createTestRouter } from './router'

interface RenderWithProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  queryClient?: ReturnType<typeof createTestQueryClient>
  router?: ReturnType<typeof createTestRouter>
  initialRoute?: string
}

/**
 * Custom render function that wraps components with TanStack providers
 * Use this for testing components that use TanStack Query or Router
 */
export function renderWithProviders(
  ui: ReactElement,
  {
    queryClient,
    router,
    initialRoute = '/',
    ...renderOptions
  }: RenderWithProvidersOptions = {}
) {
  const testQueryClient = queryClient ?? createTestQueryClient()
  const testRouter = router ?? createTestRouter({ initialEntries: [initialRoute] })

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
    ...render(ui, { wrapper: Wrapper, ...renderOptions }),
    queryClient: testQueryClient,
    router: testRouter,
  }
}

// Re-export everything from Testing Library for convenience
export * from '@testing-library/react'
export { renderWithProviders as render }
```

**Why**: Wraps components with all necessary providers for realistic testing.

### Step 9: Create MSW Setup - Handlers (~3 minutes)

**What**: Create MSW handlers for mocking API requests.

**Actions**:
Create `frontend/src/mocks/handlers.ts`:

```typescript
import { http, HttpResponse } from 'msw'

/**
 * MSW request handlers for mocking API responses
 * Add more handlers as you add more API endpoints
 */
export const handlers = [
  // Mock the example query endpoint
  http.get('/api/example', () => {
    return HttpResponse.json({
      message: 'Hello from MSW! This is a mocked response.',
      timestamp: Date.now(),
    })
  }),

  // Example: Mock error response
  http.get('/api/error-example', () => {
    return new HttpResponse(null, { status: 500 })
  }),

  // Example: Mock POST endpoint
  http.post('/api/data', async ({ request }) => {
    const body = await request.json()
    return HttpResponse.json({
      success: true,
      data: body,
    })
  }),
]
```

**Note**: These are example handlers. Add real handlers as API endpoints are added.

### Step 10: Create MSW Setup - Server (~2 minutes)

**What**: Create MSW server for Node.js environment.

**Actions**:
Create `frontend/src/mocks/server.ts`:

```typescript
import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * MSW server for Node.js (test environment)
 * This intercepts network requests during tests
 */
export const server = setupServer(...handlers)
```

**Why**: Intercepts HTTP requests at network level during tests.

### Step 11: Write Smoke Test for HomePage (~5 minutes)

**What**: Create a basic test to verify HomePage renders and queries work.

**Actions**:
Create `frontend/src/routes/HomePage.test.tsx`:

```typescript
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

  it('displays loading state initially', () => {
    renderWithProviders(<HomePage />)

    expect(screen.getByText(/loading data/i)).toBeInTheDocument()
  })

  it('fetches and displays query data', async () => {
    renderWithProviders(<HomePage />)

    // Wait for loading to finish
    await waitFor(() => {
      expect(screen.queryByText(/loading data/i)).not.toBeInTheDocument()
    })

    // Data should be displayed (from MSW mock)
    expect(screen.getByText(/hello from msw/i)).toBeInTheDocument()
  })

  it('shows navigation link to about page', () => {
    renderWithProviders(<HomePage />)

    const aboutLink = screen.getByRole('link', { name: /go to about/i })
    expect(aboutLink).toBeInTheDocument()
    expect(aboutLink).toHaveAttribute('href', '/about')
  })

  it('navigates to about page when link clicked', async () => {
    const user = userEvent.setup()
    const { router } = renderWithProviders(<HomePage />)

    const aboutLink = screen.getByRole('link', { name: /go to about/i })
    await user.click(aboutLink)

    // Check router state changed
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/about')
    })
  })
})
```

**Test coverage**:
- Rendering verification
- Loading state handling
- Query data fetching (with MSW)
- Navigation links
- Router navigation

### Step 12: Update useExampleQuery for Real API (~2 minutes)

**What**: Modify useExampleQuery to use a real endpoint that MSW can mock.

**Actions**:
Modify `frontend/src/hooks/useExampleQuery.ts`:

Change `fetchExampleData` to actually fetch from `/api/example`:

```typescript
async function fetchExampleData(): Promise<ExampleData> {
  const response = await fetch('/api/example')
  if (!response.ok) {
    throw new Error('Failed to fetch example data')
  }
  return response.json()
}
```

**Why**: Allows MSW to intercept the request in tests (mock network, not library).

### Step 13: Run Tests and Verify (~2 minutes)

**What**: Execute tests and ensure everything passes.

**Actions**:
```bash
cd /home/user/github/LimbusPlanner/frontend
yarn test run
```

**Expected results**:
- All tests pass (5 tests in HomePage.test.tsx)
- No TypeScript errors
- No runtime errors
- Tests complete in < 5 seconds

**If tests fail**:
- Check error messages carefully
- Verify all dependencies installed
- Ensure file paths are correct
- Check TypeScript configuration

### Step 14: Generate Coverage Report (~1 minute)

**What**: Run tests with coverage to verify reporting works.

**Actions**:
```bash
cd /home/user/github/LimbusPlanner/frontend
yarn test:coverage
```

**Expected results**:
- Coverage report generated in `coverage/` directory
- Text summary displayed in terminal
- HTML report available at `coverage/index.html`
- Coverage includes `src/` files (excluding tests, main.tsx, etc.)

**Check**:
- HomePage.tsx should show high coverage (tested)
- Other files may show 0% (not yet tested - that's OK)

### Step 15: Test UI Explorer (~1 minute)

**What**: Verify the UI test explorer works.

**Actions**:
```bash
cd /home/user/github/LimbusPlanner/frontend
yarn test:ui
```

**Expected results**:
- Browser opens with Vitest UI
- Can see test file tree
- Can run individual tests
- Can see test results and timing
- Can view coverage report

### Step 16: Create Test Utilities Index (~1 minute)

**What**: Create barrel export for test utilities.

**Actions**:
Create `frontend/src/test-utils/index.ts`:

```typescript
/**
 * Test utilities for LimbusPlanner frontend
 * Import from this file to get render with providers
 */
export { createTestQueryClient } from './queryClient'
export { createTestRouter } from './router'
export { renderWithProviders } from './renderWithProviders'

// Re-export Testing Library utilities
export * from '@testing-library/react'
export { userEvent } from '@testing-library/user-event'
```

**Why**: Simplifies imports in test files.

## Success Criteria

All of the following must be true for this task to be considered complete:

### Functional Requirements

- [x] Vitest v4.0.8 and all dependencies installed
- [x] Test scripts added to package.json (`test`, `test:ui`, `test:coverage`)
- [x] Vitest configured in vite.config.ts with all required options
- [x] Global test setup file (vitest.setup.ts) created and working
- [x] TypeScript configuration updated to include Vitest types
- [x] Test utilities created (queryClient, router, renderWithProviders)
- [x] MSW handlers and server configured
- [x] Smoke test for HomePage created with 5+ test cases
- [x] All tests pass when running `yarn test run`
- [x] Coverage report generates successfully
- [x] Test UI opens and works correctly

### Code Quality Requirements

- [x] No TypeScript compilation errors
- [x] No console warnings during test execution (except expected React warnings)
- [x] Test utilities are well-documented with JSDoc comments
- [x] Test files follow consistent naming convention (*.test.tsx)
- [x] All configuration values match research recommendations

### Performance Requirements

- [x] Test suite completes in < 10 seconds
- [x] Individual HomePage tests complete in < 1 second each
- [x] Coverage report generates in < 5 seconds
- [x] Test UI loads and is responsive

### Documentation Requirements

- [x] All test utility functions have clear JSDoc comments
- [x] Configuration files include inline comments explaining key settings
- [x] Critical values (retry: false, gcTime: Infinity, defaultPendingMs: 0) are commented

## Timeline

| Step | Task | Estimated Time |
|------|------|----------------|
| 1 | Install dependencies | 2 minutes |
| 2 | Add test scripts | 1 minute |
| 3 | Configure vite.config.ts | 3 minutes |
| 4 | Create vitest.setup.ts | 4 minutes |
| 5 | Update tsconfig.app.json | 1 minute |
| 6 | Create test-utils/queryClient.ts | 3 minutes |
| 7 | Create test-utils/router.tsx | 4 minutes |
| 8 | Create test-utils/renderWithProviders.tsx | 5 minutes |
| 9 | Create mocks/handlers.ts | 3 minutes |
| 10 | Create mocks/server.ts | 2 minutes |
| 11 | Write HomePage.test.tsx | 5 minutes |
| 12 | Update useExampleQuery.ts | 2 minutes |
| 13 | Run tests and verify | 2 minutes |
| 14 | Generate coverage report | 1 minute |
| 15 | Test UI explorer | 1 minute |
| 16 | Create test-utils/index.ts | 1 minute |
| **Total** | | **~40 minutes** |

**Note**: Timeline assumes no major issues. Add 10-15 minutes buffer for troubleshooting.

## Potential Issues and Solutions

### Issue 1: Peer Dependency Warnings

**Symptom**: Warnings during `yarn add` about peer dependencies.

**Cause**: React 19 is very new, some packages may not declare React 19 support yet.

**Solution**: Warnings are safe to ignore if packages are React 18+ compatible. Use `--legacy-peer-deps` if needed.

### Issue 2: Tests Hang and Don't Exit

**Symptom**: Tests pass but process doesn't exit.

**Cause**: QueryClient timers still active (gcTime issue).

**Solution**: Verify `gcTime: Infinity` in test QueryClient.

### Issue 3: Tests Are Slow

**Symptom**: Each test takes > 1 second.

**Cause**: Router `defaultPendingMs` not set to 0.

**Solution**: Verify `defaultPendingMs: 0` in createTestRouter.

### Issue 4: MSW Handlers Not Working

**Symptom**: Tests fail with network errors.

**Cause**: MSW server not started properly.

**Solution**: Verify `vitest.setup.ts` imports and starts MSW server.

### Issue 5: TypeScript Errors in Test Files

**Symptom**: "Cannot find name 'describe'" or similar.

**Cause**: Vitest globals types not configured.

**Solution**: Verify `tsconfig.app.json` includes `"vitest/globals"` in types array.

### Issue 6: Coverage Report Empty

**Symptom**: Coverage report shows 0% for all files.

**Cause**: Coverage include/exclude patterns misconfigured.

**Solution**: Check `coverage.include` and `coverage.exclude` in vite.config.ts.

### Issue 7: Import Path Errors (@/ not resolving)

**Symptom**: Tests fail with "Cannot find module '@/...'"

**Cause**: Path alias not configured for tests.

**Solution**: Verify `resolve.alias` in vite.config.ts (should already be set).

## Post-Implementation

### Immediate Next Steps

1. **Run full test suite**: `yarn test run` to ensure all tests pass
2. **Check coverage**: `yarn test:coverage` to see initial coverage metrics
3. **Commit changes**: Create git commit with descriptive message
4. **Document in code.md**: Record implementation details

### Future Enhancements

1. **Add more tests**: Test AboutPage, Button component, useExampleQuery hook
2. **Set coverage thresholds**: Add minimum coverage requirements to vite.config.ts
3. **CI/CD integration**: Add test job to GitHub Actions/GitLab CI
4. **Visual regression testing**: Consider adding Playwright or Storybook tests
5. **Performance monitoring**: Track test suite execution time over time

### Maintenance Notes

- **Update MSW handlers** as new API endpoints are added
- **Keep dependencies updated** (monthly check for updates)
- **Monitor test performance** (alert if suite takes > 30 seconds)
- **Review coverage trends** (aim for gradual increase)
- **Refactor test utilities** if patterns emerge across many tests

## Related Documentation

- Research document: `docs/00-project-initialization/01-initialize-frontend-environment/04-configure-vitest/research.md`
- Vitest docs: https://vitest.dev
- Testing Library docs: https://testing-library.com/react
- MSW docs: https://mswjs.io
- TanStack Query testing: https://tanstack.com/query/latest/docs/react/guides/testing
- TanStack Router testing: https://tanstack.com/router/latest/docs/framework/react/guide/testing

## Approval Checklist

Before running `/task-run`, verify:

- [ ] Research document has been reviewed
- [ ] Plan approach makes sense for the project
- [ ] Timeline is acceptable (~40 minutes)
- [ ] All steps are clear and actionable
- [ ] Success criteria are well-defined
- [ ] Potential issues have solutions

**Ready to proceed**: Run `/task-run docs/00-project-initialization/01-initialize-frontend-environment/04-configure-vitest` to execute this plan.
