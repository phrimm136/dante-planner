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
 *
 * @example
 * const { router } = renderWithProviders(<HomePage />)
 * await user.click(screen.getByRole('link'))
 * expect(router.state.location.pathname).toBe('/about')
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
