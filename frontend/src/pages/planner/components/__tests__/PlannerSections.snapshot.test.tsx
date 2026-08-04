/**
 * DOM snapshots of the planner's section components, one row per shape that
 * changes what they render: store-bound (editor) against explicitly-fed
 * (guide/tracker), empty against populated, read-only against editable.
 *
 * The committed snapshots were produced by an innerHTML comparison against the
 * pre-inversion components, so they record their markup exactly.
 */

import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'

import {
  PlannerEditorStoreProvider,
  createDefaultEquipment,
  createDefaultSkillEAState,
} from '../../stores/usePlannerEditorStore'
import type { PlannerEditorState } from '../../stores/usePlannerEditorStore'

import { DeckBuilderSummary, StoreBoundDeckBuilderSummary } from '../deckBuilder/DeckBuilderSummary'
import { StartBuffSection, StoreBoundStartBuffSection } from '../startBuff/StartBuffSection'
import { StartGiftSummary, StoreBoundStartGiftSummary } from '../startGift/StartGiftSummary'
import {
  EGOGiftObservationSummary,
  StoreBoundEGOGiftObservationSummary,
} from '../egoGift/EGOGiftObservationSummary'
import {
  ComprehensiveGiftSummary,
  StoreBoundComprehensiveGiftSummary,
} from '../egoGift/ComprehensiveGiftSummary'
import {
  SkillReplacementSection,
  StoreBoundSkillReplacementSection,
} from '../skillReplacement/SkillReplacementSection'

import type { SinnerEquipment, SkillEAState } from '../../types/DeckTypes'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  }
})

// ---------------------------------------------------------------------------
// Child stubs: this file pins each section's own composition, not its children.
// Every stub echoes the props it receives, so a prop rewiring shows up as a
// snapshot diff rather than passing silently.
// ---------------------------------------------------------------------------

function describeValue(value: unknown): unknown {
  if (typeof value === 'function') return 'fn'
  if (value instanceof Set) return [...value].map(String).sort()
  if (Array.isArray(value)) return value.map(describeValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .filter(([, v]) => v !== undefined)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => [k, describeValue(v)]),
    )
  }
  return value
}

function stubProps(props: Record<string, unknown>): string {
  return JSON.stringify(describeValue(props))
}

function stub(testId: string) {
  return function Stub(props: Record<string, unknown>) {
    return <div data-testid={testId} data-props={stubProps(props)} />
  }
}

vi.mock('../deckBuilder/SinnerGrid', () => ({ SinnerGrid: stub('sinner-grid') }))
vi.mock('../deckBuilder/StatusViewer', () => ({ StatusViewer: stub('status-viewer') }))
vi.mock('../deckBuilder/DeckBuilderActionBar', () => ({
  DeckBuilderActionBar: stub('deck-action-bar'),
}))
vi.mock('../startBuff/StartBuffMiniCard', () => ({ StartBuffMiniCard: stub('buff-mini-card') }))
vi.mock('../skillReplacement/SinnerSkillCard', () => ({ SinnerSkillCard: stub('sinner-skill') }))
vi.mock('../skillReplacement/SkillExchangeModal', () => ({
  SkillExchangeModal: stub('skill-exchange'),
}))
vi.mock('@/pages/egoGift/components/EGOGiftCard', () => ({ EGOGiftCard: stub('gift-card') }))
vi.mock('@/pages/egoGift/components/EGOGiftTooltip', () => ({
  EGOGiftTooltip: ({ children }: { children: ReactNode }) => (
    <div data-testid="gift-tooltip">{children}</div>
  ),
}))

// Desktop viewport, so the skill grid's column count is stable.
vi.mock('@/components/hooks/use-is-breakpoint', () => ({ useIsBreakpoint: () => true }))

// ---------------------------------------------------------------------------
// Static data
// ---------------------------------------------------------------------------

const DEFAULT_EQUIPMENT = createDefaultEquipment()

const IDENTITY_SPEC = Object.fromEntries(
  Object.values(DEFAULT_EQUIPMENT).map((eq) => [
    eq.identity.id,
    {
      rank: 3,
      updateDate: 20250101,
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

const IDENTITY_I18N = Object.fromEntries(Object.keys(IDENTITY_SPEC).map((id) => [id, `Id ${id}`]))

const EGO_SPEC = Object.fromEntries(
  Object.values(DEFAULT_EQUIPMENT).map((eq) => [
    eq.egos.ZAYIN?.id ?? '20101',
    {
      egoType: 'ZAYIN',
      skillKeywordList: [],
      battleKeywordList: [],
      attributeType: ['CRIMSON'],
      atkType: ['SLASH'],
      updateDate: 20250101,
      season: 1,
      maxThreadspin: 4,
    },
  ]),
)

const GIFT_SPEC = {
  '9001': {
    tag: ['TIER_1'],
    keyword: 'Burn',
    battleKeywordList: [],
    attributeType: 'CRIMSON',
    themePack: [],
    maxEnhancement: 2,
  },
  '9002': {
    tag: ['TIER_2'],
    keyword: 'Bleed',
    battleKeywordList: [],
    attributeType: 'AZURE',
    themePack: [],
    maxEnhancement: 2,
  },
}

const GIFT_I18N = { '9001': 'Burning Gift', '9002': 'Bleeding Gift' }

vi.mock('@/pages/identity/hooks/useIdentityListData', () => ({
  useIdentityListData: () => ({ spec: IDENTITY_SPEC, i18n: IDENTITY_I18N }),
  useIdentityListSpec: () => IDENTITY_SPEC,
}))
vi.mock('@/pages/ego/hooks/useEGOListData', () => ({
  useEGOListData: () => ({ spec: EGO_SPEC, i18n: {} }),
  useEGOListSpec: () => EGO_SPEC,
}))
vi.mock('@/pages/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({ spec: GIFT_SPEC, i18n: GIFT_I18N }),
}))
vi.mock('@/pages/egoGift/hooks/useEGOGiftObservationData', () => ({
  useEGOGiftObservationData: () => ({
    data: {
      observationEgoGiftCostDataList: [
        { egogiftCount: 0, starlightCost: 0 },
        { egogiftCount: 1, starlightCost: 5 },
        { egogiftCount: 2, starlightCost: 12 },
      ],
    },
  }),
}))

const DISPLAY_BUFFS = [
  { id: '1001', baseId: 1001, name: 'Buff One', cost: 3 },
  { id: '1002', baseId: 1002, name: 'Buff Two', cost: 5 },
]

vi.mock('../../hooks/useStartBuffSelection', () => ({
  useStartBuffSelection: () => ({ displayBuffs: DISPLAY_BUFFS }),
}))

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const EMPTY_EQUIPMENT: Record<string, SinnerEquipment> = {}
const DEPLOYMENT_ORDER = [0, 2, 4]
const SKILL_EA: Record<string, SkillEAState> = createDefaultSkillEAState()
const CURRENT_EA: Record<string, SkillEAState> = { '1': { s1: 2, s2: 3, s3: 1 } as SkillEAState }
const MD_VERSION = 7

function withStore(state: Partial<PlannerEditorState>, ui: ReactElement) {
  return <PlannerEditorStoreProvider initialState={state}>{ui}</PlannerEditorStoreProvider>
}

function html(ui: ReactElement): string {
  return render(ui).container.innerHTML
}

// ---------------------------------------------------------------------------
// Matrices
// ---------------------------------------------------------------------------

const POPULATED_STORE: Partial<PlannerEditorState> = {
  equipment: DEFAULT_EQUIPMENT,
  deploymentOrder: DEPLOYMENT_ORDER,
  selectedBuffIds: new Set([1001]),
  selectedGiftKeyword: 'Burn',
  selectedGiftIds: new Set(['9001', '9002']),
  observationGiftIds: new Set(['9001']),
  comprehensiveGiftIds: new Set(['09001', '19002']),
  skillEAState: SKILL_EA,
}

describe('DeckBuilderSummary DOM', () => {
  const MATRIX: Array<[string, () => ReactElement]> = [
    [
      'store-bound, default store deck',
      () => withStore({}, <StoreBoundDeckBuilderSummary onEditDeck={() => {}} />),
    ],
    [
      'store-bound, populated store deck, read-only',
      () => withStore(POPULATED_STORE, <StoreBoundDeckBuilderSummary readOnly />),
    ],
    [
      'explicit empty deck, read-only',
      () => <DeckBuilderSummary equipment={EMPTY_EQUIPMENT} deploymentOrder={[]} readOnly />,
    ],
    [
      'explicit populated deck, tracker mode',
      () => (
        <DeckBuilderSummary
          equipment={DEFAULT_EQUIPMENT}
          deploymentOrder={DEPLOYMENT_ORDER}
          onToggleDeploy={() => {}}
          onImport={() => {}}
          onExport={() => {}}
          onResetOrder={() => {}}
          onEditDeck={() => {}}
          trackerMode
          onResetToInitial={() => {}}
          onViewNotes={() => {}}
        />
      ),
    ],
  ]

  it.each(MATRIX)('renders %s', (_label, make) => {
    expect(html(make())).toMatchSnapshot()
  })
})

describe('StartBuffSection DOM', () => {
  const MATRIX: Array<[string, () => ReactElement]> = [
    [
      'store-bound, empty store selection',
      () => withStore({}, <StoreBoundStartBuffSection mdVersion={MD_VERSION} />),
    ],
    [
      'store-bound, populated store selection',
      () => withStore(POPULATED_STORE, <StoreBoundStartBuffSection mdVersion={MD_VERSION} />),
    ],
    [
      'explicit empty selection, read-only',
      () => <StartBuffSection mdVersion={MD_VERSION} selectedBuffIds={new Set()} readOnly />,
    ],
    [
      'explicit populated selection, read-only with notes',
      () => (
        <StartBuffSection
          mdVersion={MD_VERSION}
          selectedBuffIds={new Set([1001, 1002])}
          readOnly
          onViewNotes={() => {}}
        />
      ),
    ],
    [
      'explicit populated selection, editable',
      () => (
        <StartBuffSection
          mdVersion={MD_VERSION}
          selectedBuffIds={new Set([1001])}
          onClick={() => {}}
        />
      ),
    ],
  ]

  it.each(MATRIX)('renders %s', (_label, make) => {
    expect(html(make())).toMatchSnapshot()
  })
})

describe('StartGiftSummary DOM', () => {
  const MATRIX: Array<[string, () => ReactElement]> = [
    ['store-bound, no keyword in store', () => withStore({}, <StoreBoundStartGiftSummary />)],
    [
      'store-bound, keyword and gifts in store',
      () => withStore(POPULATED_STORE, <StoreBoundStartGiftSummary onClick={() => {}} />),
    ],
    [
      'explicit no keyword, read-only',
      () => <StartGiftSummary selectedKeyword={null} selectedGiftIds={new Set()} readOnly />,
    ],
    [
      'explicit keyword with gifts, read-only',
      () => (
        <StartGiftSummary
          selectedKeyword="Burn"
          selectedGiftIds={new Set(['9001', '9002'])}
          readOnly
          onViewNotes={() => {}}
        />
      ),
    ],
    [
      'explicit keyword without gifts, editable',
      () => (
        <StartGiftSummary selectedKeyword="Burn" selectedGiftIds={new Set()} onClick={() => {}} />
      ),
    ],
  ]

  it.each(MATRIX)('renders %s', (_label, make) => {
    expect(html(make())).toMatchSnapshot()
  })
})

describe('EGOGiftObservationSummary DOM', () => {
  const MATRIX: Array<[string, () => ReactElement]> = [
    [
      'store-bound, empty store selection',
      () => withStore({}, <StoreBoundEGOGiftObservationSummary mdVersion={MD_VERSION} />),
    ],
    [
      'store-bound, populated store selection',
      () =>
        withStore(
          POPULATED_STORE,
          <StoreBoundEGOGiftObservationSummary mdVersion={MD_VERSION} onClick={() => {}} />,
        ),
    ],
    [
      'explicit empty selection, read-only',
      () => (
        <EGOGiftObservationSummary mdVersion={MD_VERSION} selectedGiftIds={new Set()} readOnly />
      ),
    ],
    [
      'explicit populated selection, read-only with notes',
      () => (
        <EGOGiftObservationSummary
          mdVersion={MD_VERSION}
          selectedGiftIds={new Set(['9001', '9002'])}
          readOnly
          onViewNotes={() => {}}
        />
      ),
    ],
  ]

  it.each(MATRIX)('renders %s', (_label, make) => {
    expect(html(make())).toMatchSnapshot()
  })
})

describe('ComprehensiveGiftSummary DOM', () => {
  const MATRIX: Array<[string, () => ReactElement]> = [
    [
      'store-bound, empty store selection',
      () => withStore({}, <StoreBoundComprehensiveGiftSummary onClick={() => {}} />),
    ],
    [
      'store-bound, populated store selection',
      () => withStore(POPULATED_STORE, <StoreBoundComprehensiveGiftSummary onClick={() => {}} />),
    ],
    [
      'explicit empty selection',
      () => <ComprehensiveGiftSummary onClick={() => {}} selectedGiftIds={new Set()} />,
    ],
    [
      'explicit populated selection',
      () => (
        <ComprehensiveGiftSummary
          onClick={() => {}}
          selectedGiftIds={new Set(['09001', '19002'])}
        />
      ),
    ],
  ]

  it.each(MATRIX)('renders %s', (_label, make) => {
    expect(html(make())).toMatchSnapshot()
  })
})

describe('SkillReplacementSection DOM', () => {
  const MATRIX: Array<[string, () => ReactElement]> = [
    [
      'store-bound, default store state',
      () => withStore({}, <StoreBoundSkillReplacementSection />),
    ],
    [
      'store-bound, populated store state with notes',
      () =>
        withStore(POPULATED_STORE, <StoreBoundSkillReplacementSection onViewNotes={() => {}} />),
    ],
    [
      'explicit deck and planned EA, read-only',
      () => (
        <SkillReplacementSection equipment={DEFAULT_EQUIPMENT} plannedEAState={SKILL_EA} readOnly />
      ),
    ],
    [
      'explicit deck with current EA and a writer',
      () => (
        <SkillReplacementSection
          equipment={DEFAULT_EQUIPMENT}
          plannedEAState={SKILL_EA}
          currentEAState={CURRENT_EA}
          setSkillEAState={() => {}}
          onViewNotes={() => {}}
        />
      ),
    ],
    [
      'explicit empty deck',
      () => <SkillReplacementSection equipment={EMPTY_EQUIPMENT} plannedEAState={{}} readOnly />,
    ],
  ]

  it.each(MATRIX)('renders %s', (_label, make) => {
    expect(html(make())).toMatchSnapshot()
  })
})
