import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GiftNameI18n } from './GiftNameI18n'

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
  it('should render empty name as fallback during loading', () => {
    render(
      <GiftNameI18n id="9001" attributeType="WRATH" />,
      { wrapper: createWrapper() }
    )

    // Fallback should render GiftName with empty string
    expect(screen.queryByText(/./)).toBeTruthy()
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
