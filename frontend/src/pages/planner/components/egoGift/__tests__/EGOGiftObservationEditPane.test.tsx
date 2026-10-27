/**
 * EGOGiftObservationEditPane.test.tsx
 *
 * Unit tests for EGOGiftObservationEditPane component.
 * Tests dialog visibility and basic rendering.
 * Note: Component uses usePlannerStore for state management.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import type { StoreApi } from 'zustand'
import { EGOGiftObservationEditPane } from '../EGOGiftObservationEditPane'
import { usePlannerStoreApi } from '../../../stores/usePlannerStore'
import type { PlannerStore, PlannerStoreSeed } from '../../../stores/usePlannerStore'
import { SeededPlannerStore } from '../../../stores/__tests__/plannerStoreHarness'
import { asEncodedGiftId } from '@/test-utils/fixtures'
import { buildEgoGiftSpecList } from '@/test-utils'
import type { EGOGiftNameList } from '@/shared/egoGift'

// Mock react-i18next with initReactI18next for proper module loading
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        const translations: Record<string, string> = {
          'pages.plannerMD.egoGiftObservation': 'EGO Gift Observation',
          'pages.plannerMD.selectEgoGifts': 'Select EGO Gifts',
          'pages.plannerMD.selectedEgoGifts': 'Selected EGO Gifts',
          'common:reset': 'Reset',
          'common:done': 'Done',
          'deckBuilder.egoGiftSearchPlaceholder': 'Search...',
        }
        return translations[key] ?? key
      },
      i18n: { language: 'EN' },
    }),
  }
})

function StoreProbe({ onStore }: { onStore: (store: StoreApi<PlannerStore>) => void }) {
  onStore(usePlannerStoreApi())
  return null
}

function renderInStore(ui: ReactElement, seed: PlannerStoreSeed = {}) {
  let store: StoreApi<PlannerStore> | undefined
  render(
    <SeededPlannerStore mdVersion={6} initialState={seed}>
      {ui}
      <StoreProbe
        onStore={(api) => {
          store = api
        }}
      />
    </SeededPlannerStore>,
  )
  return () => {
    if (store === undefined) throw new Error('the planner store never mounted')
    return store
  }
}

// Mock observation data
const mockObservationData = {
  observationEgoGiftCostDataList: [
    { egogiftCount: 0, starlightCost: 0 },
    { egogiftCount: 1, starlightCost: 70 },
    { egogiftCount: 2, starlightCost: 160 },
    { egogiftCount: 3, starlightCost: 270 },
  ],
  observationEgoGiftDataList: ['9001', '9002', '9003', '9004'],
}

// Mock gift spec data
const mockSpec = buildEgoGiftSpecList({
  '9001': {
    tag: ['TIER_1'],
    keyword: 'Burn',
    attributeType: 'Red',
    themePack: [],
  },
  '9002': {
    tag: ['TIER_2'],
    keyword: 'Bleed',
    attributeType: 'Red',
    themePack: [],
  },
})

const mockI18n: EGOGiftNameList = {
  '9001': 'Blazing Gift',
  '9002': 'Bleeding Gift',
}

vi.mock('@/pages/egoGift/hooks/useEGOGiftObservationData', () => ({
  useEGOGiftObservationData: (_version: number) => ({
    data: mockObservationData,
  }),
}))

vi.mock('@/shared/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListSpec: () =>
    ({
      spec: mockSpec,
      i18n: mockI18n,
    }).spec,
  useEGOGiftListI18n: () =>
    ({
      spec: mockSpec,
      i18n: mockI18n,
    }).i18n,
}))

// Mock StarlightCostDisplay
vi.mock('../../StarlightCostDisplay', () => ({
  StarlightCostDisplay: ({ cost, size }: { cost: number; size: string }) => (
    <div data-testid="starlight-cost" data-cost={cost} data-size={size}>
      Cost: {cost}
    </div>
  ),
}))

// Mock Sorter
vi.mock('@/shared/filter/components/Sorter', () => ({
  Sorter: () => <select data-testid="sorter" aria-label="Sorter" />,
}))

// Mock EGOGiftKeywordFilter
vi.mock('@/pages/egoGift/components/EGOGiftKeywordFilter', () => ({
  EGOGiftKeywordFilter: () => <div data-testid="keyword-filter" />,
}))

// Mock SearchBar
vi.mock('@/shared/filter/components/SearchBar', () => ({
  SearchBar: () => <input data-testid="search-bar" />,
}))

// Mock EGOGiftSelectionList
vi.mock('@/pages/egoGift/components/EGOGiftSelectionList', () => ({
  EGOGiftSelectionList: () => <div data-testid="selection-list" data-max="3" />,
}))

// Mock EGOGiftObservationSelection
vi.mock('@/pages/egoGift/components/EGOGiftObservationSelection', () => ({
  EGOGiftObservationSelection: () => <div data-testid="selected-gifts" />,
}))

describe('EGOGiftObservationEditPane', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('dialog visibility', () => {
    it('renders dialog when open=true', () => {
      renderInStore(<EGOGiftObservationEditPane {...defaultProps} />)

      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getByText('EGO Gift Observation')).toBeInTheDocument()
    })

    it('does not render dialog when open=false', () => {
      renderInStore(<EGOGiftObservationEditPane open={false} onOpenChange={vi.fn()} />)

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  describe('cost display', () => {
    it('shows cost 0 for empty selection', () => {
      renderInStore(<EGOGiftObservationEditPane {...defaultProps} />)

      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '0')
    })
  })

  describe('filter controls', () => {
    it('renders keyword filter', () => {
      renderInStore(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('keyword-filter')).toBeInTheDocument()
    })

    it('renders sorter', () => {
      renderInStore(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('sorter')).toBeInTheDocument()
    })

    it('renders search bar', () => {
      renderInStore(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('search-bar')).toBeInTheDocument()
    })
  })

  describe('selection list layout', () => {
    it('renders selection list', () => {
      renderInStore(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('selection-list')).toBeInTheDocument()
    })

    it('renders selected gifts', () => {
      renderInStore(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('selected-gifts')).toBeInTheDocument()
    })
  })

  describe('Done button', () => {
    it('renders Done button', () => {
      renderInStore(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByRole('button', { name: 'Done' })).toBeInTheDocument()
    })

    it('calls onOpenChange(false) when Done is clicked', async () => {
      const onOpenChange = vi.fn()
      const user = userEvent.setup()

      renderInStore(<EGOGiftObservationEditPane open onOpenChange={onOpenChange} />)
      await user.click(screen.getByRole('button', { name: 'Done' }))

      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })

  describe('Reset button', () => {
    it('renders Reset button', () => {
      renderInStore(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByRole('button', { name: 'Reset' })).toBeInTheDocument()
    })

    it('empties the observation selection when Reset is clicked', async () => {
      const user = userEvent.setup()

      const getStore = renderInStore(<EGOGiftObservationEditPane {...defaultProps} />, {
        observationGiftIds: new Set([asEncodedGiftId('9001')]),
      })
      await user.click(screen.getByRole('button', { name: 'Reset' }))

      expect(getStore().getState().observationGiftIds).toEqual(new Set())
    })
  })
})
