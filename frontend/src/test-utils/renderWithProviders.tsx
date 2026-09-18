import type { ReactElement, ReactNode } from 'react'
import { render, type RenderOptions } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from './queryClient'
import { createTestRouter } from './router'

interface RenderWithProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  queryClient?: ReturnType<typeof createTestQueryClient>
  router?: ReturnType<typeof createTestRouter>
  initialRoute?: string
}

export function renderWithProviders(
  ui: ReactElement,
  { queryClient, router, initialRoute = '/', ...renderOptions }: RenderWithProvidersOptions = {},
) {
  const testQueryClient = queryClient ?? createTestQueryClient()
  const testRouter = router ?? createTestRouter({ initialEntries: [initialRoute] })

  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={testQueryClient}>{children}</QueryClientProvider>
  }

  return {
    ...render(ui, { wrapper: Wrapper, ...renderOptions }),
    queryClient: testQueryClient,
    router: testRouter,
  }
}

export * from '@testing-library/react'
export { renderWithProviders as render }
