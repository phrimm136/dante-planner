import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect } from 'react'
import type { ReactNode } from 'react'
import type { StoreApi } from 'zustand'

import {
  createDefaultDeckFilterState,
  usePlannerStore,
  usePlannerStoreApi,
} from '../../../stores/usePlannerStore'
import { SeededPlannerStore } from '../../../stores/__tests__/plannerStoreHarness'
import type { PlannerStoreState, PlannerStore } from '../../../stores/usePlannerStore'
import { DeckBuilderBlock } from '../DeckBuilderBlock'
import type { TrackerExtras } from '../DeckBuilderBlock'
import { DeckCodeTransfer } from '../DeckCodeTransfer'
import type { DeckBuilderContentProps } from '../DeckBuilderContent'
import { DEFAULT_SKILL_EA, SEASONS } from '@/shared/gameData'

const contentProps = vi.hoisted(() => [] as DeckBuilderContentProps[])

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: unknown) => (typeof fallback === 'string' ? fallback : key),
      i18n: { language: 'EN' },
    }),
  }
})

vi.mock('@/pages/identity/hooks/useIdentityListData', () => ({
  useIdentityListSpec: () => ({}),
  useIdentityListI18n: () => ({}),
}))
vi.mock('@/pages/ego/hooks/useEGOListData', () => ({
  useEGOListSpec: () => ({}),
  useEGOListI18n: () => ({}),
}))
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

vi.mock('../SinnerGrid', () => ({
  SinnerGrid: ({ onToggleDeploy }: { onToggleDeploy?: (sinnerIndex: number) => void }) => (
    <div>
      {[2, 5].map((sinnerIndex) => (
        <button key={sinnerIndex} type="button" onClick={() => onToggleDeploy?.(sinnerIndex)}>
          {`toggle ${sinnerIndex}`}
        </button>
      ))}
    </div>
  ),
}))
vi.mock('../StatusViewer', () => ({ StatusViewer: () => null }))

vi.mock('../DeckCodeTransfer', () => ({
  DeckCodeTransfer: vi.fn(
    ({
      children,
    }: {
      children: (actions: { onImport: () => void; onExport: () => void }) => ReactNode
    }) => children({ onImport: () => {}, onExport: () => {} }),
  ),
}))

vi.mock('../DeckBuilderPane', () => ({
  DeckBuilderPane: ({ open, children }: { open: boolean; children: ReactNode }) => (
    <div data-testid="deck-pane" data-open={String(open)}>
      {open && children}
    </div>
  ),
}))

vi.mock('../DeckBuilderContent', async () => {
  const { DeckFilterBar } = await import('../DeckFilterBar')
  return {
    DeckBuilderContent: (props: DeckBuilderContentProps) => {
      contentProps.push(props)
      return <DeckFilterBar />
    },
  }
})

const EGO_FILTER = { ...createDefaultDeckFilterState(), entityMode: 'ego' as const }

function Harness({ tracker }: { tracker?: TrackerExtras }) {
  return <DeckBuilderBlock {...(tracker !== undefined && { tracker })} />
}

let hostStore: StoreApi<PlannerStore> | undefined

function HostStoreProbe() {
  const api = usePlannerStoreApi()
  useEffect(() => {
    hostStore = api
  }, [api])
  return null
}

function hostState() {
  if (hostStore === undefined) throw new Error('the host store never mounted')
  return hostStore.getState()
}

function HostEntityMode() {
  const entityMode = usePlannerStore((s) => s.deckFilterState.entityMode)
  return <output data-testid="host-entity-mode">{entityMode}</output>
}

function renderBlock(ui: ReactNode, initialState: Partial<PlannerStoreState> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SeededPlannerStore initialState={initialState}>
        {ui}
        <HostEntityMode />
        <HostStoreProbe />
      </SeededPlannerStore>
    </QueryClientProvider>,
  )
}

function lastContentProps(): DeckBuilderContentProps {
  const props = contentProps.at(-1)
  if (props === undefined) throw new Error('the content never rendered')
  return props
}

beforeEach(() => {
  contentProps.length = 0
  hostStore = undefined
  vi.mocked(DeckCodeTransfer).mockClear()
})

describe('DeckBuilderBlock', () => {
  it('renders one summary, one closed pane, one code transfer and no tracker controls', () => {
    renderBlock(<Harness />)

    expect(screen.getAllByRole('button', { name: 'deckBuilder.editDeck' })).toHaveLength(1)
    expect(screen.getAllByTestId('deck-pane')).toHaveLength(1)
    expect(screen.getByTestId('deck-pane').dataset.open).toBe('false')
    expect(vi.mocked(DeckCodeTransfer)).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: 'Reset to Initial' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'viewNotes' })).toBeNull()
  })

  it('writes toggles and the order reset through the store', async () => {
    const user = userEvent.setup()
    renderBlock(<Harness />, { deploymentOrder: [2] })

    await user.click(screen.getByRole('button', { name: 'toggle 2' }))
    expect(hostState().deploymentOrder).toEqual([])

    await user.click(screen.getByRole('button', { name: 'toggle 5' }))
    expect(hostState().deploymentOrder).toEqual([5])

    await user.click(screen.getByRole('button', { name: 'deckBuilder.resetOrder' }))
    expect(hostState().deploymentOrder).toEqual([])
  })

  it('needs no deck prop: a toggle and an identity change land in the store', async () => {
    const user = userEvent.setup()
    renderBlock(<Harness />, {
      deploymentOrder: [2],
      skillEAState: { YiSang: { 0: 1, 1: 1, 2: 4 } },
    })

    await user.click(screen.getByRole('button', { name: 'toggle 5' }))
    await user.click(screen.getByRole('button', { name: 'deckBuilder.editDeck' }))
    act(() => {
      lastContentProps().onIdentityChange?.('YiSang')
    })

    expect(hostState().deploymentOrder).toEqual([2, 5])
    expect(hostState().skillEAState.YiSang).toEqual(DEFAULT_SKILL_EA)
  })

  it('opens the pane and activates the content on edit-deck', async () => {
    const user = userEvent.setup()
    renderBlock(<Harness />)

    await user.click(screen.getByRole('button', { name: 'deckBuilder.editDeck' }))

    expect(screen.getByTestId('deck-pane').dataset.open).toBe('true')
    expect(lastContentProps().isActive).toBe(true)
  })

  it("reads and writes the host store's filter state", async () => {
    const user = userEvent.setup()
    renderBlock(<Harness />, { deckFilterState: EGO_FILTER })

    await user.click(screen.getByRole('button', { name: 'deckBuilder.editDeck' }))

    expect(lastContentProps().filterState).toBe(EGO_FILTER)
    expect(screen.queryByText('Unit Keywords')).toBeNull()
    expect(screen.getByText('Additional Keywords')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'deckBuilder.entityToggle.identity' }))

    expect(screen.getByTestId('host-entity-mode').textContent).toBe('identity')
    expect(screen.getByText('Unit Keywords')).toBeInTheDocument()
  })

  it('wires the tracker extras into the summary', async () => {
    const user = userEvent.setup()
    const onResetToInitial = vi.fn()
    const onViewNotes = vi.fn()
    renderBlock(<Harness tracker={{ onResetToInitial, onViewNotes }} />)

    await user.click(screen.getByRole('button', { name: 'Reset to Initial' }))
    await user.click(screen.getByRole('button', { name: 'viewNotes' }))

    expect(onResetToInitial).toHaveBeenCalledTimes(1)
    expect(onViewNotes).toHaveBeenCalledTimes(1)
  })
})
