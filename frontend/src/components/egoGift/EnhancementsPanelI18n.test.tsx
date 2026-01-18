import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { EnhancementsPanelI18n } from './EnhancementsPanelI18n'

// Mock useEGOGiftDetailI18n hook to avoid actual data fetching
vi.mock('@/hooks/useEGOGiftDetailData', () => ({
  useEGOGiftDetailI18n: () => ({
    name: 'Test Gift Name',
    descs: ['Base description', 'Enhanced description 1', 'Enhanced description 2'],
  }),
}))

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
  it('should render enhancement panel with descriptions', () => {
    render(
      <EnhancementsPanelI18n giftId="9001" maxEnhancement={2} costs={[100, 200, 300]} />,
      { wrapper: createWrapper() }
    )

    // Should render enhancement images
    const images = screen.getAllByRole('img')
    expect(images.length).toBeGreaterThan(0)
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
