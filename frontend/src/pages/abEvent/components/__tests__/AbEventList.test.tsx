import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Suspense } from 'react'
import { AbEventList } from '../AbEventList'
import { createTestFilterStore } from '@/test-utils/filterStore'
import { AbEventIdSchema } from '@/shared/gameData'
import type { AbEventFacetState } from '../../lib/abEventFilter'
import type { AbEventSpecList } from '../../schemas/AbEventSchemas'

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

vi.mock('@/shared/assets', () => ({
  getAbEventImagePath: (id: string) => `/mock/abEvent/${id}.png`,
}))

vi.mock('@/shared/filter/hooks/useSearchTermSources', () => ({
  useSearchTermSources: vi.fn(),
}))

vi.mock('../../hooks/useAbEventListData', () => ({
  useAbEventListI18n: () => DESCS,
  AB_EVENT_LIST: { kind: 'abEvent' },
}))

import { useSearchTermSources } from '@/shared/filter'

const DESCS: Record<string, string> = {
  '901001': 'Orange circles float in the air before your eyes.',
  '901002': 'An empty room of cement.',
  '901003': 'A crowd of paper figures.',
}

const EMPTY_MAPPINGS = { keywordToValue: new Map(), unitKeywordToValue: new Map() }

const SPEC: AbEventSpecList = {
  [AbEventIdSchema.parse('901001')]: {
    relatedEgoGifts: ['9001'],
    relatedThemePacks: ['1002'],
    hasImage: true,
  },
  [AbEventIdSchema.parse('901002')]: {
    relatedEgoGifts: ['9002'],
    relatedThemePacks: ['1002'],
    hasImage: true,
  },
  [AbEventIdSchema.parse('901003')]: {
    relatedEgoGifts: [],
    relatedThemePacks: [],
    hasImage: false,
  },
}

const EMPTY_FACETS: AbEventFacetState = {
  selectedEgoGifts: new Set(),
  selectedThemePacks: new Set(),
}

function makeStore(values: Partial<AbEventFacetState> = {}, searchQuery = '') {
  return createTestFilterStore<AbEventFacetState>({ ...EMPTY_FACETS, ...values }, searchQuery)
}

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>{children}</Suspense>
      </QueryClientProvider>
    )
  }
}

function renderList(store: ReturnType<typeof makeStore>) {
  const { container } = render(<AbEventList spec={SPEC} store={store} />, {
    wrapper: createWrapper(),
  })
  return {
    total: container.querySelectorAll('a').length,
    hidden: container.querySelectorAll('div.hidden > a').length,
  }
}

describe('AbEventList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useSearchTermSources).mockReturnValue({ names: DESCS, mappings: EMPTY_MAPPINGS })
  })

  it('renders every event when nothing is filtered', () => {
    expect(renderList(makeStore())).toEqual({ total: 3, hidden: 0 })
  })

  it('leaves the keyword maps unfetched', () => {
    renderList(makeStore())

    expect(vi.mocked(useSearchTermSources).mock.calls[0]?.[2]).toBe(false)
  })

  describe('search by description', () => {
    it('matches nothing while the descriptions are still loading', () => {
      vi.mocked(useSearchTermSources).mockReturnValue({ names: {}, mappings: EMPTY_MAPPINGS })

      renderList(makeStore({}, 'cement'))

      expect(screen.getByText(/No events/)).toBeInTheDocument()
    })

    it('shows only the events whose description contains the query', () => {
      expect(renderList(makeStore({}, 'cement'))).toEqual({ total: 3, hidden: 2 })
    })

    it('is case-insensitive', () => {
      expect(renderList(makeStore({}, 'CEMENT'))).toEqual({ total: 3, hidden: 2 })
    })

    it('ANDs the query with the facets', () => {
      const store = makeStore({ selectedThemePacks: new Set(['1002']) }, 'paper')

      renderList(store)

      expect(screen.getByText(/No events/)).toBeInTheDocument()
    })
  })
})
