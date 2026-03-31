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
import { EGOList } from '../EGOList'
import type { EGOListItem } from '@/types/EGOTypes'

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
  getEGOFrameHighlightPath: () => '/mock/frame-highlight.png',
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

const mockEGOs: EGOListItem[] = [
  {
    id: '20101',
    name: 'Test EGO 1',
    egoType: 'ZAYIN',
    skillKeywordList: ['Burst', 'Combustion'],
    battleKeywordList: ['Burst', 'Combustion'],
    attributeTypes: ['CRIMSON', 'AZURE'],
    atkTypes: ['SLASH', 'PENETRATE'],
    updateDate: 20240101,
    season: 1,
  },
  {
    id: '20201',
    name: 'Test EGO 2',
    egoType: 'TETH',
    skillKeywordList: ['Charge'],
    battleKeywordList: ['Charge'],
    attributeTypes: ['AZURE'],
    atkTypes: ['PENETRATE'],
    updateDate: 20240102,
    season: 2,
  },
  {
    id: '20301',
    name: 'Test EGO 3',
    egoType: 'HE',
    skillKeywordList: ['Burst'],
    battleKeywordList: ['Burst'],
    attributeTypes: ['VIOLET'],
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
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // All EGOs should be visible (ResponsiveCardGrid renders twice: mobile + desktop)
      const cards = screen.getAllByRole('link')
      expect(cards).toHaveLength(3) // Single unified grid
    })

    it('shows empty state when no EGOs match filters', () => {
      render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set(['NonExistentSinner'])}
          selectedKeywords={new Set()}
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      expect(screen.getByText(/No E\.G\.Os match/)).toBeInTheDocument()
    })
  })

  describe('filtering', () => {
    it('filters by EGO type', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set(['ZAYIN'])}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Only ZAYIN EGOs should be visible (hidden class is on parent div)
      // Count wrapper divs with hidden class
      const hiddenWrappers = container.querySelectorAll('div.hidden > a[role="link"]')
      const allCards = container.querySelectorAll('a[role="link"]')

      expect(allCards.length).toBe(3) // Single unified grid
      expect(hiddenWrappers.length).toBe(2) // 2 hidden in single grid
    })

    it('filters by skill attribute with AND logic (single)', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set(['AZURE'])}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // EGOs with AZURE: 20101 (CRIMSON+AZURE) and 20201 (AZURE)
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // VIOLET (20301) hidden
    })

    it('filters by skill attribute with AND logic (multiple)', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set(['CRIMSON', 'AZURE'])}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Only 20101 has BOTH CRIMSON and AZURE
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 20201 and 20301 hidden
    })

    it('filters by attack type with AND logic (single)', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set(['PENETRATE'])}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // EGOs with PENETRATE: 20101 (SLASH+PENETRATE) and 20201 (PENETRATE)
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // HIT (20301) hidden
    })

    it('filters by attack type with AND logic (multiple)', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set(['SLASH', 'PENETRATE'])}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Only 20101 has BOTH SLASH and PENETRATE
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 20201 and 20301 hidden
    })

    it('filters by season', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set([1])}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Season 1 EGOs only (hidden class is on parent div)
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3) // Single unified grid
      expect(hiddenCards.length).toBe(1) // Season 2 (20201) should be hidden
    })

    it('applies AND logic between filter types', () => {
      const { container } = render(
        <EGOList
          egos={mockEGOs}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set(['CRIMSON'])}
          selectedAtkTypes={new Set(['SLASH'])}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Must have CRIMSON attribute AND SLASH attack type
      // Only 20101 has both (CRIMSON+AZURE attributes, SLASH+PENETRATE attacks)
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 20201 and 20301 hidden
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
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery="rupture"
        />,
        { wrapper: createWrapper() }
      )

      // Search returns no results when mappings are loading
      expect(screen.getByText(/No E\.G\.Os match/)).toBeInTheDocument()
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
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery="rupture"
        />,
        { wrapper: createWrapper() }
      )

      // EGOs with Burst keyword should be visible (hidden class is on parent div)
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3) // Single unified grid
      expect(hiddenCards.length).toBe(1) // 20101 and 20301 have 'Burst', so only 20201 hidden × 2 grids
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
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set()}
          selectedSeasons={new Set()}
          searchQuery="CHARGE"
        />,
        { wrapper: createWrapper() }
      )

      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3) // Single unified grid
      expect(hiddenCards.length).toBe(2) // Only 'CHARGE' (20201) matches, 20101 and 20301 hidden
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
          selectedBattleKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedEGOTypes={new Set(['ZAYIN'])}
          selectedSeasons={new Set()}
          searchQuery="rupture"
        />,
        { wrapper: createWrapper() }
      )

      // Must match ZAYIN type AND have Burst keyword (hidden class is on parent div)
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3) // Single unified grid
      expect(hiddenCards.length).toBe(2) // Only 20101 matches both filters, 20201 and 20301 hidden
    })
  })
})
