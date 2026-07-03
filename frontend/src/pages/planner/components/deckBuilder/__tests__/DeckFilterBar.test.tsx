/**
 * DeckFilterBar.test.tsx
 *
 * Integration tests for the deck builder filter bar.
 * Verifies mode-dependent visibility, Reset All behavior,
 * inert-selection preservation across mode toggles, and the
 * mobile chevron expansion toggle.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { DeckFilterBar } from '../DeckFilterBar'
import {
  PlannerEditorStoreProvider,
  usePlannerEditorStoreApi,
} from '../../../stores/usePlannerEditorStore'
import { SEASONS } from '@/shared/gameData'

import type { ReactNode } from 'react'
import type { StoreApi } from 'zustand'
import type { PlannerEditorStore } from '../../../stores/usePlannerEditorStore'

// i18n: return fallback text if provided, otherwise the key
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

// Dropdown i18n data (Season + UnitKeyword)
vi.mock('@/shared/filter/hooks/useFilterI18nData', () => ({
  useFilterI18nData: () => ({
    seasonsI18n: Object.fromEntries(SEASONS.map((s) => [String(s), `Season ${s}`])),
    unitKeywordsI18n: {},
  }),
}))

// Battle keyword dropdown data
vi.mock('@/shared/gameText/hooks/useKeywordListData', () => ({
  useKeywordListSpec: () => ({}),
  useKeywordListI18n: () => ({}),
}))

// Viewport detection — default to desktop; mobile test overrides
const isBreakpointMock = vi.fn<() => boolean>(() => true)
vi.mock('@/components/hooks/use-is-breakpoint', () => ({
  useIsBreakpoint: () => isBreakpointMock(),
}))

/**
 * Captures the store API from within the provider so tests can read/write
 * filter state without rendering the deck builder's heavy children.
 */
function StoreCapture({
  onReady,
}: {
  onReady: (api: StoreApi<PlannerEditorStore>) => void
}) {
  const api = usePlannerEditorStoreApi()
  onReady(api)
  return null
}

function renderWithStore(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  let storeApi: StoreApi<PlannerEditorStore> | null = null

  const utils = render(
    <QueryClientProvider client={queryClient}>
      <PlannerEditorStoreProvider>
        <StoreCapture
          onReady={(api) => {
            storeApi = api
          }}
        />
        {ui}
      </PlannerEditorStoreProvider>
    </QueryClientProvider>
  )

  if (!storeApi) {
    throw new Error('Store API was not captured')
  }

  return { ...utils, storeApi: storeApi as StoreApi<PlannerEditorStore> }
}

describe('DeckFilterBar', () => {
  beforeEach(() => {
    isBreakpointMock.mockReturnValue(true)
  })

  it('renders all identity-mode filter controls including Def, Rank, UnitKw (no EgoType)', () => {
    renderWithStore(<DeckFilterBar />)

    // Toggle buttons are present
    expect(screen.getByRole('button', { name: /identity/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /ego/i })).toBeInTheDocument()

    // Dropdown comboboxes: Season, Unit Keywords, Additional Keywords (3 in identity mode)
    const comboboxes = screen.getAllByRole('combobox')
    expect(comboboxes).toHaveLength(3)
    expect(screen.getByText('Season')).toBeInTheDocument()
    expect(screen.getByText('Unit Keywords')).toBeInTheDocument()
    expect(screen.getByText('Additional Keywords')).toBeInTheDocument()

    // Reset All button
    expect(screen.getByRole('button', { name: /Reset All/i })).toBeInTheDocument()
  })

  it('hides Def, Rank, UnitKw and shows EgoType when switched to EGO mode', async () => {
    const user = userEvent.setup()
    const { storeApi } = renderWithStore(<DeckFilterBar />)

    await user.click(screen.getByRole('button', { name: /ego/i }))

    expect(storeApi.getState().deckFilterState.entityMode).toBe('ego')

    // In EGO mode: Season + Additional Keywords remain (2 comboboxes). No UnitKeywords.
    const comboboxes = screen.getAllByRole('combobox')
    expect(comboboxes).toHaveLength(2)
    expect(screen.getByText('Season')).toBeInTheDocument()
    expect(screen.getByText('Additional Keywords')).toBeInTheDocument()
    expect(screen.queryByText('Unit Keywords')).toBeNull()
  })

  it('Reset All clears every filter set and searchQuery but preserves entityMode', async () => {
    const user = userEvent.setup()
    const { storeApi } = renderWithStore(<DeckFilterBar />)

    // Seed state directly via the store
    storeApi.setState((s) => ({
      deckFilterState: {
        ...s.deckFilterState,
        entityMode: 'ego',
        selectedSinners: new Set(['YiSang']),
        selectedKeywords: new Set(['Burst']),
        selectedAttributes: new Set(['AZURE']),
        selectedAtkTypes: new Set(['SLASH']),
        selectedDefTypes: new Set(['GUARD']),
        selectedRaritys: new Set([3]),
        selectedEgoTypes: new Set(['ALEPH']),
        selectedSeasons: new Set([1]),
        selectedUnitKeywords: new Set(['BLADE_LINEAGE']),
        selectedBattleKeywords: new Set(['Poise']),
        searchQuery: 'query',
      },
    }))

    await user.click(screen.getByRole('button', { name: /Reset All/i }))

    const next = storeApi.getState().deckFilterState
    expect(next.entityMode).toBe('ego')
    expect(next.selectedSinners.size).toBe(0)
    expect(next.selectedKeywords.size).toBe(0)
    expect(next.selectedAttributes.size).toBe(0)
    expect(next.selectedAtkTypes.size).toBe(0)
    expect(next.selectedDefTypes.size).toBe(0)
    expect(next.selectedRaritys.size).toBe(0)
    expect(next.selectedEgoTypes.size).toBe(0)
    expect(next.selectedSeasons.size).toBe(0)
    expect(next.selectedUnitKeywords.size).toBe(0)
    expect(next.selectedBattleKeywords.size).toBe(0)
    expect(next.searchQuery).toBe('')
  })

  it('preserves inert selections in the store across mode toggles', async () => {
    const user = userEvent.setup()
    const { storeApi } = renderWithStore(<DeckFilterBar />)

    // Seed a DefType (identity-only) and an EgoType (ego-only) upfront
    storeApi.setState((s) => ({
      deckFilterState: {
        ...s.deckFilterState,
        selectedDefTypes: new Set(['GUARD']),
        selectedEgoTypes: new Set(['ALEPH']),
      },
    }))

    // Starting mode is identity; toggle to EGO
    await user.click(screen.getByRole('button', { name: /ego/i }))

    // DefType should still be in the store (only UI hides the chip)
    expect(storeApi.getState().deckFilterState.selectedDefTypes.has('GUARD')).toBe(true)

    // Toggle back to identity
    await user.click(screen.getByRole('button', { name: /identity/i }))

    // EgoType should still be in the store
    expect(storeApi.getState().deckFilterState.selectedEgoTypes.has('ALEPH')).toBe(true)
    expect(storeApi.getState().deckFilterState.selectedDefTypes.has('GUARD')).toBe(true)
  })

  it('mobile chevron toggles secondary filter visibility via aria-expanded', async () => {
    isBreakpointMock.mockReturnValue(false)
    const user = userEvent.setup()
    renderWithStore(<DeckFilterBar />)

    const chevron = screen.getByRole('button', { name: /Expand filters/i })
    expect(chevron).toHaveAttribute('aria-expanded', 'false')

    // Initially collapsed: Skill Attribute section is not rendered
    expect(screen.queryByText('Skill Attribute')).toBeNull()

    // Expand
    await user.click(chevron)
    expect(chevron).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Skill Attribute')).toBeInTheDocument()

    // Collapse again
    await user.click(chevron)
    expect(chevron).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Skill Attribute')).toBeNull()
  })
})
