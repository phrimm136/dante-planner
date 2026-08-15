/**
 * The tracker's deck pane mounts the real catalog subtree — filter bar and both
 * grids — which read deck UI state from the planner editor store. The tracker
 * viewer itself sits outside any provider, so this file mounts the pane the way
 * the viewer does and drives a filter through it.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'

import { createDefaultEquipment } from '../../../stores/usePlannerEditorStore'
import { TrackerDeckBuilderContent } from '../DeckBuilderContent'
import { SEASONS } from '@/shared/gameData'

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

const DEFAULT_EQUIPMENT = createDefaultEquipment()

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
  useIdentityListData: () => ({ spec: IDENTITY_SPEC, i18n: IDENTITY_I18N }),
}))
vi.mock('@/pages/ego/hooks/useEGOListData', () => ({
  useEGOListSpec: () => EGO_SPEC,
  useEGOListI18n: () => EGO_I18N,
  useEGOListData: () => ({ spec: EGO_SPEC, i18n: EGO_I18N }),
}))
vi.mock('@/shared/filter/hooks/useSearchMappings', () => ({
  useSearchMappings: () => ({
    keywordToValue: new Map<string, string[]>(),
    unitKeywordToValue: new Map<string, string[]>(),
  }),
}))

const TRACKER_DECK = {
  equipment: DEFAULT_EQUIPMENT,
  setEquipment: () => {},
  deploymentOrder: [0, 3],
  setDeploymentOrder: () => {},
}

const ACTIONS = {
  onImport: () => {},
  onExport: () => {},
  onResetOrder: () => {},
  onIdentityChange: () => {},
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
  it('mounts the real catalog subtree outside the planner editor provider', () => {
    expect(() =>
      renderPane(<TrackerDeckBuilderContent isActive {...TRACKER_DECK} {...ACTIONS} />),
    ).not.toThrow()

    expect(screen.getByRole('button', { name: /Reset All/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /identity/i })).toBeInTheDocument()
  })

  it('drives its own filter state: switching to EGO mode re-renders the bar', async () => {
    const user = userEvent.setup()
    renderPane(<TrackerDeckBuilderContent isActive {...TRACKER_DECK} {...ACTIONS} />)

    // Identity mode shows Unit Keywords; EGO mode does not.
    expect(screen.getByText('Unit Keywords')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'deckBuilder.entityToggle.ego' }))

    expect(screen.queryByText('Unit Keywords')).toBeNull()
    expect(screen.getByText('Additional Keywords')).toBeInTheDocument()
  })

  it('keeps two panes independent: filtering one does not touch the other', async () => {
    const user = userEvent.setup()
    const { container } = renderPane(
      <div>
        <div data-testid="pane-a">
          <TrackerDeckBuilderContent isActive {...TRACKER_DECK} {...ACTIONS} />
        </div>
        <div data-testid="pane-b">
          <TrackerDeckBuilderContent isActive {...TRACKER_DECK} {...ACTIONS} />
        </div>
      </div>,
    )

    const paneA = within(screen.getByTestId('pane-a'))
    await user.click(paneA.getByRole('button', { name: 'deckBuilder.entityToggle.ego' }))

    expect(paneA.queryByText('Unit Keywords')).toBeNull()
    expect(within(screen.getByTestId('pane-b')).getByText('Unit Keywords')).toBeInTheDocument()
    expect(container).toBeTruthy()
  })

  it('feeds its filter state into the grid: a search query hides non-matching cards', async () => {
    const user = userEvent.setup()
    const { container } = renderPane(
      <TrackerDeckBuilderContent isActive {...TRACKER_DECK} {...ACTIONS} />,
    )

    expect(shownIdentityNames(container).length).toBeGreaterThan(1)

    const search = screen.getAllByRole('textbox')[0]
    await user.type(search, 'no such identity')

    // SearchBar debounces before it writes the query back.
    await waitFor(() => {
      expect(shownIdentityNames(container)).toEqual([])
    })
  })
})
