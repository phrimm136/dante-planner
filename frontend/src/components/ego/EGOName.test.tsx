/**
 * EGOName.test.tsx
 *
 * Tests for EGOName component that fetches and displays EGO names.
 * This component uses useSuspenseQuery, so it must be wrapped in Suspense.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { EGOName } from './EGOName'

// Mock the data hook
vi.mock('@/hooks/useEGOListData', () => ({
  useEGOListI18n: vi.fn(),
}))

import { useEGOListI18n } from '@/hooks/useEGOListData'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={<span data-testid="loading">Loading...</span>}>
          {children}
        </Suspense>
      </QueryClientProvider>
    )
  }
}

describe('EGOName', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the name from i18n data', async () => {
    vi.mocked(useEGOListI18n).mockReturnValue({
      '20101': 'Fluid Sac',
      '20102': 'Dimension Shredder',
    })

    render(<EGOName id="20101" />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Fluid Sac')).toBeInTheDocument()
    })
  })

  it('falls back to ID when name not found', async () => {
    vi.mocked(useEGOListI18n).mockReturnValue({})

    render(<EGOName id="99999" />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('99999')).toBeInTheDocument()
    })
  })

  it('renders different names for different IDs', async () => {
    vi.mocked(useEGOListI18n).mockReturnValue({
      '20101': 'Fluid Sac',
      '20102': 'Dimension Shredder',
    })

    const { rerender } = render(<EGOName id="20101" />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Fluid Sac')).toBeInTheDocument()
    })

    rerender(
      <QueryClientProvider client={new QueryClient()}>
        <Suspense fallback={<span>Loading...</span>}>
          <EGOName id="20102" />
        </Suspense>
      </QueryClientProvider>
    )

    // Mock needs to be called again for new ID lookup
    vi.mocked(useEGOListI18n).mockReturnValue({
      '20101': 'Fluid Sac',
      '20102': 'Dimension Shredder',
    })

    await waitFor(() => {
      expect(screen.getByText('Dimension Shredder')).toBeInTheDocument()
    })
  })
})
