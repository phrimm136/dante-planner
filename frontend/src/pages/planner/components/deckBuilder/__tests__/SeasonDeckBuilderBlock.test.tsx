import { describe, it, expect, vi, assert } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement, ReactNode } from 'react'

import { PlannerStoreProvider, createDefaultEquipment } from '../../../stores/usePlannerStore'
import { DeckBuilderBlock } from '../DeckBuilderBlock'
import { SEASONS } from '@/shared/gameData'
import { PLANNER_CONFIG } from '@/lib/constants'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: string) => fallback ?? key,
      i18n: { language: 'EN' },
    }),
  }
})

vi.mock('@/shared/filter/hooks/useFilterI18nData', () => ({
  useFilterI18nData: () => ({
    seasonsI18n: Object.fromEntries(SEASONS.map((s) => [String(s), `Season ${s}`])),
    unitKeywordsI18n: {},
  }),
}))

vi.mock('@/shared/gameText/hooks/useKeywordListData', () => ({
  useKeywordListSpec: () => ({}),
  useKeywordListI18n: () => ({}),
}))

vi.mock('@/components/hooks/use-is-breakpoint', () => ({
  useIsBreakpoint: () => true,
}))

vi.mock('../DeckBuilderPane', () => ({
  DeckBuilderPane: ({ open, children }: { open: boolean; children: ReactNode }) =>
    open ? <div>{children}</div> : null,
}))

vi.mock('../DeckBuilderSummary', () => ({
  DeckBuilderSummary: ({ onEditDeck }: { onEditDeck: () => void }) => (
    <button type="button" onClick={onEditDeck}>
      edit deck
    </button>
  ),
}))

const DEFAULT_EQUIPMENT = createDefaultEquipment(60)

const IDENTITY_SPEC = Object.fromEntries(
  Object.values(DEFAULT_EQUIPMENT).map((eq, index) => [
    eq.identity.id,
    {
      rank: 3,
      updateDate: 20250101 + index,
      unitKeywordList: [],
      skillKeywordList: [],
      battleKeywordList: [],
      attributeType: ['CRIMSON', 'AZURE', 'SCARLET'],
      atkType: ['SLASH', 'PIERCE', 'BLUNT'],
      defenseType: 'GUARD',
      season: 1,
    },
  ]),
)

const EGO_SPEC = Object.fromEntries(
  Object.values(DEFAULT_EQUIPMENT).map((eq, index) => [
    eq.egos.ZAYIN?.id ?? `2${String(index + 1).padStart(2, '0')}01`,
    {
      egoType: 'ZAYIN',
      skillKeywordList: [],
      battleKeywordList: [],
      attributeType: ['CRIMSON'],
      atkType: ['SLASH'],
      updateDate: 20250101 + index,
      season: 1,
      maxThreadspin: 4,
    },
  ]),
)

const IDENTITY_I18N = Object.fromEntries(Object.keys(IDENTITY_SPEC).map((id) => [id, `Id ${id}`]))
const EGO_I18N = Object.fromEntries(Object.keys(EGO_SPEC).map((id) => [id, `Ego ${id}`]))

vi.mock('@/pages/identity/hooks/useIdentityListData', () => ({
  useIdentityListSpec: () => IDENTITY_SPEC,
  useIdentityListI18n: () => IDENTITY_I18N,
}))
vi.mock('@/pages/ego/hooks/useEGOListData', () => ({
  useEGOListSpec: () => EGO_SPEC,
  useEGOListI18n: () => EGO_I18N,
}))
vi.mock('@/shared/filter/hooks/useSearchMappings', () => ({
  useSearchMappings: () => ({
    keywordToValue: new Map<string, string[]>(),
    unitKeywordToValue: new Map<string, string[]>(),
  }),
}))

function TrackerDeckBlock() {
  return (
    <PlannerStoreProvider identityMaxLevel={60} initialState={{ deploymentOrder: [0, 3] }}>
      <DeckBuilderBlock
        mdVersion={PLANNER_CONFIG.mdCurrentVersion}
        tracker={{ onResetToInitial: () => {} }}
      />
    </PlannerStoreProvider>
  )
}

function renderPane(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

/** Identity cards the catalog is showing; filtered-out ones sit under `.hidden`. */
function shownIdentityNames(container: HTMLElement): string[] {
  return [...container.querySelectorAll('img[alt^="Id "]')]
    .filter((img) => !img.closest('.hidden'))
    .map((img) => img.getAttribute('alt') ?? '')
}

describe('tracker deck pane', () => {
  it('mounts the real catalog subtree inside a planner store', async () => {
    const user = userEvent.setup()
    renderPane(<TrackerDeckBlock />)
    await user.click(screen.getByRole('button', { name: 'edit deck' }))

    expect(await screen.findByRole('button', { name: /Reset All/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /identity/i })).toBeInTheDocument()
  })

  it('drives its own filter state: switching to EGO mode re-renders the bar', async () => {
    const user = userEvent.setup()
    renderPane(<TrackerDeckBlock />)
    await user.click(screen.getByRole('button', { name: 'edit deck' }))

    // Identity mode shows Unit Keywords; EGO mode does not.
    expect(await screen.findByText('Unit Keywords')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'deckBuilder.entityToggle.ego' }))

    expect(screen.queryByText('Unit Keywords')).toBeNull()
    expect(screen.getByText('Additional Keywords')).toBeInTheDocument()
  })

  it('keeps two panes independent: filtering one does not touch the other', async () => {
    const user = userEvent.setup()
    const { container } = renderPane(
      <div>
        <div data-testid="pane-a">
          <TrackerDeckBlock />
        </div>
        <div data-testid="pane-b">
          <TrackerDeckBlock />
        </div>
      </div>,
    )

    const paneA = within(screen.getByTestId('pane-a'))
    const paneB = within(screen.getByTestId('pane-b'))
    await user.click(paneA.getByRole('button', { name: 'edit deck' }))
    await user.click(paneB.getByRole('button', { name: 'edit deck' }))
    await paneB.findByText('Unit Keywords')
    await user.click(paneA.getByRole('button', { name: 'deckBuilder.entityToggle.ego' }))

    expect(paneA.queryByText('Unit Keywords')).toBeNull()
    expect(paneB.getByText('Unit Keywords')).toBeInTheDocument()
    expect(container).toBeTruthy()
  })

  it('feeds its filter state into the grid: a search query hides non-matching cards', async () => {
    const user = userEvent.setup()
    const { container } = renderPane(<TrackerDeckBlock />)
    await user.click(screen.getByRole('button', { name: 'edit deck' }))

    await waitFor(() => {
      expect(shownIdentityNames(container).length).toBeGreaterThan(1)
    })

    const search = screen.getAllByRole('textbox')[0]
    assert(search, 'the pane renders no search textbox')
    await user.type(search, 'no such identity')

    // SearchBar debounces before it writes the query back.
    await waitFor(() => {
      expect(shownIdentityNames(container)).toEqual([])
    })
  })
})
