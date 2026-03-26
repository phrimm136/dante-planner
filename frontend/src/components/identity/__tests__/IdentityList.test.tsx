/**
 * IdentityList.test.tsx
 *
 * Tests for IdentityList component with non-suspending search mappings.
 * Verifies filtering behavior and graceful handling of loading state.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Suspense } from 'react'
import { IdentityList } from '../IdentityList'
import type { IdentityListItem } from '@/types/IdentityTypes'

// Mock TanStack Router Link component
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, params }: { children: React.ReactNode; to: string; params?: Record<string, string> }) => (
    <a href={params?.id ? `${to.replace('$id', params.id)}` : to} role="link">{children}</a>
  ),
}))

// Mock asset paths
vi.mock('@/lib/assetPaths', () => ({
  getIdentityInfoImagePath: (id: string) => `/mock/identity/${id}.png`,
  getIdentityImageFallbackPath: (id: string) => `/mock/identity/${id}-fallback.png`,
  getUptieFramePath: () => '/mock/uptie-frame.png',
  getIdentityFrameHighlightPath: () => '/mock/frame-highlight.png',
  getSinnerBGPath: () => '/mock/sinner-bg.png',
  getSinnerIconPath: () => '/mock/sinner.png',
  getRarityIconPath: () => '/mock/rarity.png',
}))

// Mock search mappings - non-suspending version
vi.mock('@/hooks/useSearchMappings', () => ({
  useSearchMappingsDeferred: vi.fn(),
}))

// Mock IdentityListI18n for IdentityName component
vi.mock('@/hooks/useIdentityListData', () => ({
  useIdentityListI18n: () => ({
    '10101': 'Test Identity 1',
    '10201': 'Test Identity 2',
    '10301': 'Test Identity 3',
  }),
  useIdentityListI18nDeferred: () => ({
    '10101': 'Test Identity 1',
    '10201': 'Test Identity 2',
    '10301': 'Test Identity 3',
  }),
}))

import { useSearchMappingsDeferred } from '@/hooks/useSearchMappings'

const mockIdentities: IdentityListItem[] = [
  {
    id: '10101',
    name: 'Test Identity 1',
    rank: 3,
    skillKeywordList: ['Burst', 'Combustion'],
    unitKeywordList: ['TheBlueReverberation'],
    attributeTypes: ['CRIMSON', 'AZURE'],
    atkTypes: ['SLASH', 'PENETRATE'],
    defenseTypes: ['GUARD'],
    updateDate: 20240101,
    season: 1,
  },
  {
    id: '10201',
    name: 'Test Identity 2',
    rank: 2,
    skillKeywordList: ['Charge'],
    unitKeywordList: ['SevenAssociation'],
    attributeTypes: ['AZURE'],
    atkTypes: ['PENETRATE'],
    defenseTypes: ['EVADE'],
    updateDate: 20240102,
    season: 2,
  },
  {
    id: '10301',
    name: 'Test Identity 3',
    rank: 3,
    skillKeywordList: ['Burst'],
    unitKeywordList: ['TheBlueReverberation'],
    attributeTypes: ['VIOLET'],
    atkTypes: ['HIT'],
    defenseTypes: ['COUNTER'],
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

describe('IdentityList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Default: return empty mappings (loading state)
    vi.mocked(useSearchMappingsDeferred).mockReturnValue({
      keywordToValue: new Map(),
      unitKeywordToValue: new Map(),
    })
  })

  describe('rendering', () => {
    it('renders all identities when no filters applied', () => {
      render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      const cards = screen.getAllByRole('link')
      expect(cards).toHaveLength(3)
    })

    it('shows empty state when no identities match filters', () => {
      render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set(['NonExistentSinner'])}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      expect(screen.getByText(/No Identities match/)).toBeInTheDocument()
    })
  })

  describe('filtering', () => {
    it('filters by skill attribute with AND logic (single)', () => {
      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set(['AZURE'])}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Identities with AZURE: 10101 (CRIMSON+AZURE) and 10201 (AZURE)
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // VIOLET (10301) hidden
    })

    it('filters by skill attribute with AND logic (multiple)', () => {
      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set(['CRIMSON', 'AZURE'])}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Only 10101 has BOTH CRIMSON and AZURE
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })

    it('filters by attack type with AND logic (single)', () => {
      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set(['PENETRATE'])}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Identities with PENETRATE: 10101 (SLASH+PENETRATE) and 10201 (PENETRATE)
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // HIT (10301) hidden
    })

    it('filters by attack type with AND logic (multiple)', () => {
      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set(['SLASH', 'PENETRATE'])}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Only 10101 has BOTH SLASH and PENETRATE
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })

    it('filters by rarity', () => {
      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set([3])}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Rank 3: 10101 and 10301
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // 10201 (rank 2) hidden
    })

    it('filters by season', () => {
      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set([1])}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Season 1: 10101 and 10301
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // Season 2 (10201) hidden
    })

    it('filters by unit keyword', () => {
      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set(['SevenAssociation'])}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Only 10201 has SevenAssociation
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10101 and 10301 hidden
    })

    it('filters by keyword with AND logic', () => {
      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set(['Burst', 'Combustion'])}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Only 10101 has BOTH Burst and Combustion
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })

    it('applies AND logic between filter types', () => {
      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set(['CRIMSON'])}
          selectedAtkTypes={new Set(['SLASH'])}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery=""
        />,
        { wrapper: createWrapper() }
      )

      // Must have CRIMSON attribute AND SLASH attack type
      // Only 10101 has both
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })
  })

  describe('search with deferred mappings', () => {
    it('returns no results when mappings are loading (empty)', () => {
      vi.mocked(useSearchMappingsDeferred).mockReturnValue({
        keywordToValue: new Map(),
        unitKeywordToValue: new Map(),
      })

      render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery="rupture"
        />,
        { wrapper: createWrapper() }
      )

      expect(screen.getByText(/No Identities match/)).toBeInTheDocument()
    })

    it('filters by keyword search when mappings are loaded', () => {
      vi.mocked(useSearchMappingsDeferred).mockReturnValue({
        keywordToValue: new Map([
          ['rupture', ['Burst']],
          ['burn', ['Combustion']],
          ['charge', ['Charge']],
        ]),
        unitKeywordToValue: new Map(),
      })

      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery="rupture"
        />,
        { wrapper: createWrapper() }
      )

      // Identities with Burst keyword: 10101 and 10301
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // 10201 hidden
    })

    it('search is case-insensitive', () => {
      vi.mocked(useSearchMappingsDeferred).mockReturnValue({
        keywordToValue: new Map([
          ['charge', ['Charge']],
        ]),
        unitKeywordToValue: new Map(),
      })

      const { container } = render(
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set()}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery="CHARGE"
        />,
        { wrapper: createWrapper() }
      )

      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // Only 10201 matches
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
        <IdentityList
          identities={mockIdentities}
          selectedSinners={new Set()}
          selectedKeywords={new Set()}
          selectedAttributes={new Set(['CRIMSON'])}
          selectedAtkTypes={new Set()}
          selectedDefTypes={new Set()}
          selectedRaritys={new Set()}
          selectedSeasons={new Set()}
          selectedUnitKeywords={new Set()}
          searchQuery="rupture"
        />,
        { wrapper: createWrapper() }
      )

      // Must have CRIMSON attribute AND match "rupture" search (Burst keyword)
      // Only 10101 has both
      const hiddenCards = container.querySelectorAll('div.hidden > a[role="link"]')
      const totalCards = container.querySelectorAll('a[role="link"]')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })
  })
})
