import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GiftNameI18n } from './GiftNameI18n'

// Mock useEGOGiftDetailI18n hook to avoid actual data fetching
vi.mock('@/hooks/useEGOGiftDetailData', () => ({
  useEGOGiftDetailI18n: () => ({
    name: 'Test Gift Name',
    descs: [],
  }),
}))

// Mock useColorCodes hook used by GiftName
vi.mock('@/hooks/useColorCodes', () => ({
  useColorCodes: () => ({
    data: {
      WRATH: '#ff0000',
      LUST: '#ff6600',
    },
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

describe('GiftNameI18n', () => {
  it('should render gift name from i18n data', () => {
    render(
      <GiftNameI18n id="9001" attributeType="WRATH" />,
      { wrapper: createWrapper() }
    )

    // Should render the name from mocked i18n data
    expect(screen.getByText('Test Gift Name')).toBeDefined()
  })

  it('should not suspend parent component', () => {
    const { container } = render(
      <GiftNameI18n id="9001" attributeType="WRATH" />,
      { wrapper: createWrapper() }
    )

    // Component should render immediately (internal Suspense handles loading)
    expect(container.firstChild).toBeTruthy()
  })
})
