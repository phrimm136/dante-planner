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
import { EGOName } from '../EGOName'

// Mock the data hook
vi.mock('../../hooks/useEGOListData', () => ({
  useEGOListI18n: vi.fn(),
}))

import { useEGOListI18n } from '../../hooks/useEGOListData'
import { asEGOId } from '@/test-utils/fixtures'

const EGO_20101 = asEGOId('20101')
const EGO_20102 = asEGOId('20102')
const EGO_UNNAMED = asEGOId('21299')

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={<span data-testid="loading">Loading...</span>}>{children}</Suspense>
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
      [EGO_20101]: 'Fluid Sac',
      [EGO_20102]: 'Dimension Shredder',
    })

    render(<EGOName id={EGO_20101} />, { wrapper: createWrapper() })

    // AutoSizeWrappedText renders text twice (hidden measurement + visible display)
    await waitFor(() => {
      expect(screen.getAllByText('Fluid Sac').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('falls back to ID when name not found', async () => {
    vi.mocked(useEGOListI18n).mockReturnValue({})

    render(<EGOName id={EGO_UNNAMED} />, { wrapper: createWrapper() })

    // AutoSizeWrappedText renders text twice (hidden measurement + visible display)
    await waitFor(() => {
      expect(screen.getAllByText('21299').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('renders different names for different IDs', async () => {
    vi.mocked(useEGOListI18n).mockReturnValue({
      [EGO_20101]: 'Fluid Sac',
      [EGO_20102]: 'Dimension Shredder',
    })

    const { rerender } = render(<EGOName id={EGO_20101} />, { wrapper: createWrapper() })

    // AutoSizeWrappedText renders text twice (hidden measurement + visible display)
    await waitFor(() => {
      expect(screen.getAllByText('Fluid Sac').length).toBeGreaterThanOrEqual(1)
    })

    rerender(
      <QueryClientProvider client={new QueryClient()}>
        <Suspense fallback={<span>Loading...</span>}>
          <EGOName id={EGO_20102} />
        </Suspense>
      </QueryClientProvider>,
    )

    // Mock needs to be called again for new ID lookup
    vi.mocked(useEGOListI18n).mockReturnValue({
      [EGO_20101]: 'Fluid Sac',
      [EGO_20102]: 'Dimension Shredder',
    })

    await waitFor(() => {
      expect(screen.getAllByText('Dimension Shredder').length).toBeGreaterThanOrEqual(1)
    })
  })
})
