/**
 * DOM snapshots of the deck builder, one row per mode signal that changes what
 * it renders: store-bound (editor) against session-owned (tracker), identity
 * against EGO, active against a dialog that is closing, cold against warmed.
 *
 * The committed snapshots were produced by an innerHTML comparison against the
 * pre-inversion component, so they record its markup exactly.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, act } from '@testing-library/react'
import type { ReactElement } from 'react'

import {
  PlannerEditorStoreProvider,
  createDefaultEquipment,
  createDefaultDeckFilterState,
} from '../../../stores/usePlannerEditorStore'
import type { PlannerEditorState } from '../../../stores/usePlannerEditorStore'

import {
  DeckBuilderContent,
  StoreBoundDeckBuilderContent,
  TrackerDeckBuilderContent,
} from '../DeckBuilderContent'

import type { SinnerEquipment } from '../../../types/DeckTypes'

// ---------------------------------------------------------------------------
// Child stubs: this file pins the builder's own composition, not its children.
// Every stub echoes the props it receives, so a prop rewiring shows up as a
// snapshot diff rather than passing silently.
// ---------------------------------------------------------------------------

function describeValue(value: unknown): unknown {
  if (typeof value === 'function') return 'fn'
  if (value instanceof Set) return [...value].map(String).sort()
  if (Array.isArray(value)) return value.map(describeValue)
  if (value && typeof value === 'object') {
    if ('current' in value) return 'ref'
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .filter(([, v]) => v !== undefined)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => [k, describeValue(v)]),
    )
  }
  return value
}

function stub(testId: string) {
  return function Stub(props: Record<string, unknown>) {
    return <div data-testid={testId} data-props={JSON.stringify(describeValue(props))} />
  }
}

vi.mock('../CompactIdentityRow', () => ({ CompactIdentityRow: stub('compact-identity-row') }))
vi.mock('../CompactEgoGrid', () => ({ CompactEgoGrid: stub('compact-ego-grid') }))
vi.mock('../StatusViewer', () => ({ StatusViewer: stub('status-viewer') }))
vi.mock('../DeckBuilderActionBar', () => ({ DeckBuilderActionBar: stub('deck-action-bar') }))
vi.mock('../DeckFilterBar', () => ({ DeckFilterBar: stub('deck-filter-bar') }))
vi.mock('../IdentityGrid', () => ({ IdentityGrid: stub('identity-grid') }))
vi.mock('../EgoGrid', () => ({ EgoGrid: stub('ego-grid') }))

// ---------------------------------------------------------------------------
// Static data
// ---------------------------------------------------------------------------

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

vi.mock('@/pages/identity/hooks/useIdentityListData', () => ({
  useIdentityListData: () => ({
    spec: IDENTITY_SPEC,
    i18n: Object.fromEntries(Object.keys(IDENTITY_SPEC).map((id) => [id, `Id ${id}`])),
  }),
}))
vi.mock('@/pages/ego/hooks/useEGOListData', () => ({
  useEGOListData: () => ({
    spec: EGO_SPEC,
    i18n: Object.fromEntries(Object.keys(EGO_SPEC).map((id) => [id, `Ego ${id}`])),
  }),
}))
vi.mock('@/shared/filter/hooks/useSearchMappings', () => ({
  useSearchMappingsDeferred: () => ({
    keywordToValue: new Map<string, string[]>(),
    unitKeywordToValue: new Map<string, string[]>(),
  }),
}))

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

/** A deck that differs from the default, so equipped-id sets are non-trivial. */
const SWAPPED_EQUIPMENT: Record<string, SinnerEquipment> = {
  ...DEFAULT_EQUIPMENT,
  '1': {
    identity: { id: '10201', uptie: 3, level: 40 },
    egos: { ZAYIN: { id: '20201', threadspin: 3 } },
  },
}

const EGO_FILTER = { ...createDefaultDeckFilterState(), entityMode: 'ego' as const }
const SEARCHED_FILTER = { ...createDefaultDeckFilterState(), searchQuery: 'Id 10101' }

const ACTIONS = {
  onImport: () => {},
  onExport: () => {},
  onResetOrder: () => {},
  onIdentityChange: () => {},
}

const TRACKER_DECK = {
  equipment: SWAPPED_EQUIPMENT,
  setEquipment: () => {},
  deploymentOrder: [0, 3],
  setDeploymentOrder: () => {},
}

function withStore(state: Partial<PlannerEditorState>, ui: ReactElement) {
  return <PlannerEditorStoreProvider initialState={state}>{ui}</PlannerEditorStoreProvider>
}

function html(ui: ReactElement): string {
  return render(ui).container.innerHTML
}

/** The inactive grid only mounts once the idle warm-up has run. */
async function warmedHtml(ui: ReactElement): Promise<string> {
  const { container } = render(ui)
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 300))
  })
  return container.innerHTML
}

const MATRIX: Array<[string, () => ReactElement]> = [
  [
    'store-bound, identity mode, active',
    () => withStore({}, <StoreBoundDeckBuilderContent isActive {...ACTIONS} />),
  ],
  [
    'store-bound, identity mode, closing dialog',
    () => withStore({}, <StoreBoundDeckBuilderContent isActive={false} {...ACTIONS} />),
  ],
  [
    'store-bound, ego mode, swapped deck',
    () =>
      withStore(
        { equipment: SWAPPED_EQUIPMENT, deploymentOrder: [1, 2], deckFilterState: EGO_FILTER },
        <StoreBoundDeckBuilderContent isActive {...ACTIONS} />,
      ),
  ],
  [
    'store-bound, identity mode, search filter applied',
    () =>
      withStore(
        { deckFilterState: SEARCHED_FILTER },
        <StoreBoundDeckBuilderContent isActive {...ACTIONS} />,
      ),
  ],
  [
    'tracker deck, active',
    () => <TrackerDeckBuilderContent isActive {...TRACKER_DECK} {...ACTIONS} />,
  ],
  [
    'tracker deck, closing dialog',
    () => <TrackerDeckBuilderContent isActive={false} {...TRACKER_DECK} {...ACTIONS} />,
  ],
  [
    'explicit ego-mode filter with an empty deck',
    () => (
      <DeckBuilderContent
        isActive
        equipment={{}}
        setEquipment={() => {}}
        deploymentOrder={[]}
        setDeploymentOrder={() => {}}
        filterState={EGO_FILTER}
        {...ACTIONS}
      />
    ),
  ],
]

describe('DeckBuilderContent DOM', () => {
  it.each(MATRIX)('renders %s', (_label, make) => {
    expect(html(make())).toMatchSnapshot()
  })
})

describe('DeckBuilderContent DOM after idle warm-up', () => {
  it('renders both grids once the inactive one has warmed', async () => {
    expect(
      await warmedHtml(withStore({}, <StoreBoundDeckBuilderContent isActive {...ACTIONS} />)),
    ).toMatchSnapshot()
  })

  it('renders both grids for a tracker deck once warmed', async () => {
    expect(
      await warmedHtml(<TrackerDeckBuilderContent isActive {...TRACKER_DECK} {...ACTIONS} />),
    ).toMatchSnapshot()
  })
})
