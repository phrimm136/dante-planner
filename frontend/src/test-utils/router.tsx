import type { ReactNode } from 'react'
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
 * - Sets defaultPendingMs to 0 to prevent slow tests (critical!)
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
