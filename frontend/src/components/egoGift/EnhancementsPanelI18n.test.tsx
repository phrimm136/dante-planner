import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { EnhancementsPanelI18n } from './EnhancementsPanelI18n'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('EnhancementsPanelI18n', () => {
  it('should render structure during loading with fallback', () => {
    render(
      <EnhancementsPanelI18n giftId="9001" maxEnhancement={2} costs={[100, 200, 300]} />,
      { wrapper: createWrapper() }
    )

    // Fallback should render AllEnhancementsPanel structure
    expect(screen.queryByRole('img')).toBeTruthy()
  })

  it('should not suspend parent component', () => {
    const { container } = render(
      <EnhancementsPanelI18n giftId="9001" maxEnhancement={1} costs={[100, 200]} />,
      { wrapper: createWrapper() }
    )

    // Component should render immediately (internal Suspense handles loading)
    expect(container.firstChild).toBeTruthy()
  })
})
