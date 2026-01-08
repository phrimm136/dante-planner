/**
 * EGOList.test.tsx
 *
 * Tests for EGOList component with non-suspending search mappings.
 * Verifies filtering behavior and graceful handling of loading state.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Suspense } from 'react'
import { EGOList } from './EGOList'
import type { EGO } from '@/types/EGOTypes'

// Mock TanStack Router Link component
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, params }: { children: React.ReactNode; to: string; params?: Record<string, string> }) => (
    <a href={params?.id ? `${to.replace('$id', params.id)}` : to} role="link">{children}</a>
  ),
}))

// Mock asset paths
vi.mock('@/lib/assetPaths', () => ({
  getEGOImagePath: (id: string) => `/mock/ego/${id}.png`,
  getEGOFramePath: () => '/mock/frame.png',
  getEGORankIconPath: () => '/mock/rank.png',
  getEGOSmallRankIconPath: () => '/mock/small-rank.png',
  getEGOTierIconPath: () => '/mock/tier.png',
  getEGOInfoPanelPath: () => '/mock/panel.png',
  getSinnerIconPath: () => '/mock/sinner.png',
  getSinnerBGPath: () => '/mock/sinner-bg.png',
}))

// Mock search mappings - non-suspending version
vi.mock('@/hooks/useSearchMappings', () => ({
  useSearchMappingsDeferred: vi.fn(),
}))

// Mock EGOListI18n for EGOName component
vi.mock('@/hooks/useEGOListData', () => ({
  useEGOListI18n: () => ({
    '20101': 'Test EGO 1',
    '20201': 'Test EGO 2',
    '20301': 'Test EGO 3',
  }),
  useEGOListI18nDeferred: () => ({
    '20101': 'Test EGO 1',
    '20201': 'Test EGO 2',
    '20301': 'Test EGO 3',
  }),
}))

import { useSearchMappingsDeferred } from '@/hooks/useSearchMappings'

const mockEGOs: EGO[] = [
  {
    id: '20101',
    name: 'Test EGO 1',
    egoType: 'ZAYIN',
    skillKeywordList: ['Burst', 'Combustion'],
    attributeTypes: ['Wrath'],
    atkTypes: ['SLASH'],
    updateDate: 20240101,
    season: 1,
  },
  {
    id: '20201',
    name: 'Test EGO 2',
    egoType: 'TETH',
    skillKeywordList: ['Charge'],
    attributeTypes: ['Lust'],
    atkTypes: ['PENETRATE'],
    updateDate: 20240102,
    season: 2,
  },
  {
    id: '20301',
    name: 'Test EGO 3',
    egoType: 'HE',
    skillKeywordList: ['Burst'],
    attributeTypes: ['Sloth'],
    atkTypes: ['HIT'],
    updateDate: 20240103,
    season: 1,
  },
]

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          {children}
        </Suspense>
      </QueryClientProvider>
    )
  }
}

describe('EGOList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Default: return empty mappings (loading state)
    vi.mocked(useSearchMappingsDeferred).mockReturnValue({
      keywordToValue: new Map(),
      unitKeywordToValue: new Map(),
    })
  })

  describe('rendering', () => {
    it('renders all EGOs when no filters applied', () => {
      render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // All EGOs should be visible (not have 'hidden' class)
      const cards = screen.getAllByRole('link')
      expect(cards).toHaveLength(3)
    })

    it('shows empty state when no EGOs match filters', () => {
      render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set(['NonExistentSinner'])}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      expect(screen.getByText(/No EGOs match/)).toBeInTheDocument()
    })
  })

  describe('filtering', () => {
    it('filters by EGO type', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set(['ZAYIN'])}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Only ZAYIN EGOs should be visible (hidden class is on parent div)
      const hiddenCards = container.querySelectorAll('.hidden')
      const allCards = container.querySelectorAll('a[role="link"]')

      expect(allCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2)
    })

    it('filters by skill attribute', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set(['Wrath', 'Lust'])}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // EGOs with Wrath or Lust attributes should be visible (hidden class is on parent div)
      const hiddenDivs = container.querySelectorAll('div.hidden')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenDivs.length).toBe(1) // Sloth should be hidden
    })

    it('filters by attack type', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set(['SLASH'])}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Only SLASH attack type EGOs (hidden class is on parent div)
      const hiddenDivs = container.querySelectorAll('div.hidden')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenDivs.length).toBe(2) // Only SLASH (20101) should be visible
    })

    it('filters by season', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set([1])}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Season 1 EGOs only (hidden class is on parent div)
      const hiddenDivs = container.querySelectorAll('div.hidden')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenDivs.length).toBe(1) // Season 2 (20201) should be hidden
    })

    it('applies AND logic between filter types', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set(['Wrath'])}
          selectedAtkTypes={new Set(['SLASH'])}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Must have Wrath AND SLASH (hidden class is on parent div)
      const hiddenDivs = container.querySelectorAll('div.hidden')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenDivs.length).toBe(2) // Only 20101 has both Wrath AND SLASH
    })
  })

  describe('search with deferred mappings', () => {
    it('returns no results when mappings are loading (empty)', () => {
      // Mappings are empty (loading state)
      vi.mocked(useSearchMappingsDeferred).mockReturnValue({
        keywordToValue: new Map(),
        unitKeywordToValue: new Map(),
      })

      render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery="rupture"
        />,
        { wrapper: createWrapper() }
      )

      // Search returns no results when mappings are loading
      expect(screen.getByText(/No EGOs match/)).toBeInTheDocument()
    })

    it('filters by keyword search when mappings are loaded', () => {
      // Mappings are loaded
      vi.mocked(useSearchMappingsDeferred).mockReturnValue({
        keywordToValue: new Map([
          ['rupture', ['Burst']],
          ['burn', ['Combustion']],
          ['charge', ['Charge']],
        ]),
        unitKeywordToValue: new Map(),
      })

      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery="rupture"
        />,
        { wrapper: createWrapper() }
      )

      // EGOs with Burst keyword should be visible (hidden class is on parent div)
      const hiddenDivs = container.querySelectorAll('div.hidden')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenDivs.length).toBe(1) // 20101 and 20301 have 'Burst', so only 20201 hidden
    })

    it('search is case-insensitive', () => {
      vi.mocked(useSearchMappingsDeferred).mockReturnValue({
        keywordToValue: new Map([
          ['charge', ['Charge']],
        ]),
        unitKeywordToValue: new Map(),
      })

      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery="CHARGE"
        />,
        { wrapper: createWrapper() }
      )

      const hiddenDivs = container.querySelectorAll('div.hidden')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenDivs.length).toBe(2) // Only 'CHARGE' matches, rest hidden
    })
  })

  describe('combined filters and search', () => {
    it('applies both filters and search together', () => {
      vi.mocked(useSearchMappingsDeferred).mockReturnValue({
        keywordToValue: new Map([
          ['rupture', ['Burst']],
        ]),
        unitKeywordToValue: new Map(),
      })

      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set(['ZAYIN'])}
          selectedSeasons={new Set()}
          searchQuery="rupture"
        />,
        { wrapper: createWrapper() }
      )

      // Must match ZAYIN type AND have Burst keyword (hidden class is on parent div)
      const hiddenDivs = container.querySelectorAll('div.hidden')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenDivs.length).toBe(2) // Only 20101 matches both filters
    })
  })
})
