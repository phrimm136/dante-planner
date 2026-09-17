/**
 * IdentityList.test.tsx
 *
 * Tests for IdentityList component with non-suspending search mappings.
 * Verifies filtering behavior and graceful handling of loading state.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Suspense } from 'react'
import { IdentityList } from '../IdentityList'
import { createTestFilterStore } from '@/test-utils/filterStore'
import { asIdentityId } from '@/test-utils/fixtures'
import type { IdentityFacetState } from '../../lib/identityFilter'
import type { IdentityEntity } from '../../types/IdentityTypes'

// Mock TanStack Router Link component
vi.mock('@tanstack/react-router', () => ({
  Link: ({
    children,
    to,
    params,
  }: {
    children: React.ReactNode
    to: string
    params?: Record<string, string>
  }) => <a href={params?.id ? `${to.replace('$id', params.id)}` : to}>{children}</a>,
}))

// Mock asset paths
vi.mock('@/shared/assets', () => ({
  getIdentityInfoImagePath: (id: string) => `/mock/identity/${id}.png`,
  getIdentityImageFallbackPath: (id: string) => `/mock/identity/${id}-fallback.png`,
  getUptieFramePath: () => '/mock/uptie-frame.png',
  getIdentityHoverRingPath: () => '/mock/frame-highlight.png',
  getIdentityMaskPath: () => '/mock/identity-mask.png',
  getSinnerIconRingPath: () => '/mock/sinner-bg.png',
  getSinnerFacePath: () => '/mock/sinner-face.png',
  getIdentityGradePath: () => '/mock/grade.png',
}))

// Mock search mappings - non-suspending version
vi.mock('@/shared/filter/hooks/useSearchTermSources', () => ({
  useSearchTermSources: vi.fn(),
}))

// Mock IdentityListI18n for IdentityName component
vi.mock('../../hooks/useIdentityListData', () => ({
  useIdentityListI18n: () => ({
    '10101': 'Test Identity 1',
    '10201': 'Test Identity 2',
    '10301': 'Test Identity 3',
  }),
  IDENTITY_LIST: { kind: 'identity' },
}))

import { useSearchTermSources } from '@/shared/filter'

const IDENTITY_10101 = asIdentityId('10101')
const IDENTITY_10201 = asIdentityId('10201')
const IDENTITY_10301 = asIdentityId('10301')

const IDENTITY_NAMES = {
  '10101': 'Test Identity 1',
  '10201': 'Test Identity 2',
  '10301': 'Test Identity 3',
}

const mockIdentities: IdentityEntity[] = [
  {
    id: IDENTITY_10101,
    name: 'Test Identity 1',
    rank: 3,
    skillKeywordList: ['Burst', 'Combustion'],
    battleKeywordList: ['Burst', 'Combustion', 'Aggro'],
    unitKeywordList: ['TheBlueReverberation'],
    attributeType: ['CRIMSON', 'AZURE'],
    atkType: ['SLASH', 'PENETRATE'],
    defenseType: ['GUARD'],
    updateDate: 20240101,
    season: 1,
  },
  {
    id: IDENTITY_10201,
    name: 'Test Identity 2',
    rank: 2,
    skillKeywordList: ['Charge'],
    battleKeywordList: ['Charge'],
    unitKeywordList: ['SevenAssociation'],
    attributeType: ['AZURE'],
    atkType: ['PENETRATE'],
    defenseType: ['EVADE'],
    updateDate: 20240102,
    season: 2,
  },
  {
    id: IDENTITY_10301,
    name: 'Test Identity 3',
    rank: 3,
    skillKeywordList: ['Burst'],
    battleKeywordList: ['Burst', 'Aggro'],
    unitKeywordList: ['TheBlueReverberation'],
    attributeType: ['VIOLET'],
    atkType: ['HIT'],
    defenseType: ['COUNTER'],
    updateDate: 20240103,
    season: 1,
  },
]

const EMPTY_FACETS: IdentityFacetState = {
  selectedSinners: new Set(),
  selectedKeywords: new Set(),
  selectedBattleKeywords: new Set(),
  selectedAttributes: new Set(),
  selectedAtkTypes: new Set(),
  selectedDefTypes: new Set(),
  selectedRaritys: new Set(),
  selectedSeasons: new Set(),
  selectedUnitKeywords: new Set(),
}

function makeStore(values: Partial<IdentityFacetState> = {}, searchQuery = '') {
  return createTestFilterStore<IdentityFacetState>({ ...EMPTY_FACETS, ...values }, searchQuery)
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>{children}</Suspense>
      </QueryClientProvider>
    )
  }
}

// The grid's reveal window opens on the first animation frame; every slot is empty before it.
function renderRevealed(...args: Parameters<typeof render>) {
  const result = render(...args)
  act(() => {
    vi.advanceTimersToNextFrame()
  })
  return result
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('IdentityList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Default: return empty mappings (loading state)
    vi.mocked(useSearchTermSources).mockReturnValue({
      names: IDENTITY_NAMES,
      mappings: {
        keywordToValue: new Map(),
        unitKeywordToValue: new Map(),
      },
    })
  })

  describe('rendering', () => {
    it('renders all identities when no filters applied', () => {
      renderRevealed(<IdentityList identities={mockIdentities} store={makeStore()} />, {
        wrapper: createWrapper(),
      })

      const cards = screen.getAllByRole('link')
      expect(cards).toHaveLength(3)
    })

    it('shows empty state when no identities match filters', () => {
      renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedSinners: new Set(['NonExistentSinner']) })}
        />,
        { wrapper: createWrapper() },
      )

      expect(screen.getByText(/No Identities match/)).toBeInTheDocument()
    })
  })

  describe('filtering', () => {
    it('filters by skill attribute with AND logic (single)', () => {
      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedAttributes: new Set(['AZURE']) })}
        />,
        { wrapper: createWrapper() },
      )

      // Identities with AZURE: 10101 (CRIMSON+AZURE) and 10201 (AZURE)
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // VIOLET (10301) hidden
    })

    it('filters by skill attribute with AND logic (multiple)', () => {
      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedAttributes: new Set(['CRIMSON', 'AZURE']) })}
        />,
        { wrapper: createWrapper() },
      )

      // Only 10101 has BOTH CRIMSON and AZURE
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })

    it('filters by attack type with AND logic (single)', () => {
      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedAtkTypes: new Set(['PENETRATE']) })}
        />,
        { wrapper: createWrapper() },
      )

      // Identities with PENETRATE: 10101 (SLASH+PENETRATE) and 10201 (PENETRATE)
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // HIT (10301) hidden
    })

    it('filters by attack type with AND logic (multiple)', () => {
      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedAtkTypes: new Set(['SLASH', 'PENETRATE']) })}
        />,
        { wrapper: createWrapper() },
      )

      // Only 10101 has BOTH SLASH and PENETRATE
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })

    it('filters by rarity', () => {
      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedRaritys: new Set([3]) })}
        />,
        { wrapper: createWrapper() },
      )

      // Rank 3: 10101 and 10301
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // 10201 (rank 2) hidden
    })

    it('filters by season', () => {
      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedSeasons: new Set([1]) })}
        />,
        { wrapper: createWrapper() },
      )

      // Season 1: 10101 and 10301
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // Season 2 (10201) hidden
    })

    it('filters by unit keyword', () => {
      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedUnitKeywords: new Set(['SevenAssociation']) })}
        />,
        { wrapper: createWrapper() },
      )

      // Only 10201 has SevenAssociation
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10101 and 10301 hidden
    })

    it('filters by keyword with AND logic', () => {
      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedKeywords: new Set(['Burst', 'Combustion']) })}
        />,
        { wrapper: createWrapper() },
      )

      // Only 10101 has BOTH Burst and Combustion
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })

    it('applies AND logic between filter types', () => {
      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({
            selectedAttributes: new Set(['CRIMSON']),
            selectedAtkTypes: new Set(['SLASH']),
          })}
        />,
        { wrapper: createWrapper() },
      )

      // Must have CRIMSON attribute AND SLASH attack type
      // Only 10101 has both
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })
  })

  describe('search with deferred mappings', () => {
    it('returns no results when mappings are loading (empty)', () => {
      vi.mocked(useSearchTermSources).mockReturnValue({
        names: IDENTITY_NAMES,
        mappings: {
          keywordToValue: new Map(),
          unitKeywordToValue: new Map(),
        },
      })

      renderRevealed(
        <IdentityList identities={mockIdentities} store={makeStore({}, 'rupture')} />,
        {
          wrapper: createWrapper(),
        },
      )

      expect(screen.getByText(/No Identities match/)).toBeInTheDocument()
    })

    it('filters by keyword search when mappings are loaded', () => {
      vi.mocked(useSearchTermSources).mockReturnValue({
        names: IDENTITY_NAMES,
        mappings: {
          keywordToValue: new Map([
            ['rupture', ['Burst']],
            ['burn', ['Combustion']],
            ['charge', ['Charge']],
          ]),
          unitKeywordToValue: new Map(),
        },
      })

      const { container } = renderRevealed(
        <IdentityList identities={mockIdentities} store={makeStore({}, 'rupture')} />,
        { wrapper: createWrapper() },
      )

      // Identities with Burst keyword: 10101 and 10301
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(1) // 10201 hidden
    })

    it('search is case-insensitive', () => {
      vi.mocked(useSearchTermSources).mockReturnValue({
        names: IDENTITY_NAMES,
        mappings: {
          keywordToValue: new Map([['charge', ['Charge']]]),
          unitKeywordToValue: new Map(),
        },
      })

      const { container } = renderRevealed(
        <IdentityList identities={mockIdentities} store={makeStore({}, 'CHARGE')} />,
        { wrapper: createWrapper() },
      )

      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // Only 10201 matches
    })
  })

  describe('combined filters and search', () => {
    it('applies both filters and search together', () => {
      vi.mocked(useSearchTermSources).mockReturnValue({
        names: IDENTITY_NAMES,
        mappings: {
          keywordToValue: new Map([['rupture', ['Burst']]]),
          unitKeywordToValue: new Map(),
        },
      })

      const { container } = renderRevealed(
        <IdentityList
          identities={mockIdentities}
          store={makeStore({ selectedAttributes: new Set(['CRIMSON']) }, 'rupture')}
        />,
        { wrapper: createWrapper() },
      )

      // Must have CRIMSON attribute AND match "rupture" search (Burst keyword)
      // Only 10101 has both
      const hiddenCards = container.querySelectorAll('div.hidden > a')
      const totalCards = container.querySelectorAll('a')
      expect(totalCards.length).toBe(3)
      expect(hiddenCards.length).toBe(2) // 10201 and 10301 hidden
    })
  })
})
