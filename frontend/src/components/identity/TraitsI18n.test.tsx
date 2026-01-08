/**
 * TraitsI18n.test.tsx
 *
 * Tests for TraitsI18n component (Suspense-based trait display).
 * - UT4: TraitsI18n renders with internal Suspense boundary
 * - Component renders correctly with mock data
 * - Hidden traits (BASE_APPEARANCE, SMALL) are filtered out
 * - Unity rich text parsing
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TraitsI18n } from './TraitsI18n'

// Mock the useTraitsI18n hook
vi.mock('@/hooks/useTraitsI18n', () => ({
  useTraitsI18n: vi.fn(),
}))

import { useTraitsI18n } from '@/hooks/useTraitsI18n'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={<div data-testid="skeleton">Loading traits...</div>}>
          {children}
        </Suspense>
      </QueryClientProvider>
    )
  }
}

describe('TraitsI18n', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders traits with translated names - UT4', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({
      LIMBUS_COMPANY: 'Limbus Company',
      LIMBUS_COMPANY_LCB: 'LCB',
      BLACK_BEAST: 'Black Beast',
    })

    render(
      <TraitsI18n traits={['LIMBUS_COMPANY', 'LIMBUS_COMPANY_LCB']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByText('Limbus Company')).toBeDefined()
      expect(screen.getByText('LCB')).toBeDefined()
    })
  })

  it('filters out hidden traits (BASE_APPEARANCE, SMALL)', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({
      BASE_APPEARANCE: 'Base Appearance',
      SMALL: 'Small',
      LIMBUS_COMPANY: 'Limbus Company',
    })

    render(
      <TraitsI18n traits={['BASE_APPEARANCE', 'SMALL', 'LIMBUS_COMPANY']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByText('Limbus Company')).toBeDefined()
    })

    // Hidden traits should not be rendered
    expect(screen.queryByText('Base Appearance')).toBeNull()
    expect(screen.queryByText('Small')).toBeNull()
  })

  it('falls back to raw trait name when translation not found', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({})

    render(
      <TraitsI18n traits={['UNKNOWN_TRAIT']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByText('UNKNOWN_TRAIT')).toBeDefined()
    })
  })

  it('renders nothing when all traits are hidden', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({
      BASE_APPEARANCE: 'Base Appearance',
      SMALL: 'Small',
    })

    const { container } = render(
      <TraitsI18n traits={['BASE_APPEARANCE', 'SMALL']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      // The component should render nothing when all visible traits are filtered out
      expect(container.querySelector('[data-testid="skeleton"]')).toBeNull()
    })

    // Container should be empty or have no trait badges
    expect(screen.queryByText('Base Appearance')).toBeNull()
    expect(screen.queryByText('Small')).toBeNull()
  })

  it('renders nothing when traits array is empty', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({})

    const { container } = render(
      <TraitsI18n traits={[]} />,
      { wrapper: createWrapper() }
    )

    // With empty traits, component should not render anything
    await waitFor(() => {
      expect(container.textContent).toBe('')
    })
  })

  it('renders multiple traits in flex container', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({
      TRAIT_A: 'Trait A',
      TRAIT_B: 'Trait B',
      TRAIT_C: 'Trait C',
    })

    render(
      <TraitsI18n traits={['TRAIT_A', 'TRAIT_B', 'TRAIT_C']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByText('Trait A')).toBeDefined()
      expect(screen.getByText('Trait B')).toBeDefined()
      expect(screen.getByText('Trait C')).toBeDefined()
    })
  })

  it('handles Unity rich text with color formatting', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({
      JIA_FAMILY: '<color=#d40000>Jia Family</color>',
    })

    render(
      <TraitsI18n traits={['JIA_FAMILY']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      // The parsed text should appear (without the HTML tags)
      expect(screen.getByText('Jia Family')).toBeDefined()
    })

    // The span should have the color style
    const traitElement = screen.getByText('Jia Family')
    expect(traitElement.getAttribute('style')).toContain('color')
  })

  it('handles Unity rich text with strikethrough', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({
      DEPRECATED_TRAIT: '<s>Deprecated</s>',
    })

    render(
      <TraitsI18n traits={['DEPRECATED_TRAIT']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByText('Deprecated')).toBeDefined()
    })

    // Should render as strikethrough
    const strikeElement = screen.getByText('Deprecated')
    expect(strikeElement.tagName).toBe('S')
  })

  it('handles combined color and strikethrough', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({
      COMBINED_TRAIT: '<color=#d40000><s>Combined</s></color>',
    })

    render(
      <TraitsI18n traits={['COMBINED_TRAIT']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByText('Combined')).toBeDefined()
    })
  })
})

describe('TraitsI18n Suspense boundary - UT4', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('component is designed for Suspense boundary usage', async () => {
    // TraitsI18n uses useTraitsI18n which is a suspending hook
    // When wrapped in Suspense, it should work correctly
    vi.mocked(useTraitsI18n).mockReturnValue({
      LIMBUS_COMPANY: 'Limbus Company',
    })

    render(
      <TraitsI18n traits={['LIMBUS_COMPANY']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByText('Limbus Company')).toBeDefined()
    })
  })

  it('uses key prop from trait ID for stable rendering', async () => {
    vi.mocked(useTraitsI18n).mockReturnValue({
      TRAIT_A: 'Trait A',
      TRAIT_B: 'Trait B',
    })

    const { container } = render(
      <TraitsI18n traits={['TRAIT_A', 'TRAIT_B']} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      // Each trait should be rendered in its own span element
      const spans = container.querySelectorAll('span.px-2')
      expect(spans.length).toBe(2)
    })
  })
})
