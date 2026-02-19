/**
 * EGOGiftObservationEditPane.test.tsx
 *
 * Unit tests for EGOGiftObservationEditPane component.
 * Tests dialog visibility and basic rendering.
 * Note: Component uses usePlannerEditorStore for state management.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EGOGiftObservationEditPane } from './EGOGiftObservationEditPane'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'

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

// Mock planner editor store (component uses store directly)
const mockSetObservationGiftIds = vi.fn()
vi.mock('@/stores/usePlannerEditorStore', () => ({
  usePlannerEditorStore: (selector: (state: Record<string, unknown>) => unknown) => {
    const mockState = {
      observationGiftIds: new Set<string>(),
      setObservationGiftIds: mockSetObservationGiftIds,
    }
    return selector(mockState)
  },
}))

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
const mockSpec: Record<string, EGOGiftSpec> = {
  '9001': {
    tag: ['TIER_1'] as EGOGiftSpec['tag'],
    keyword: 'Burn',
    attributeType: 'Red',
    themePack: [],
  },
  '9002': {
    tag: ['TIER_2'] as EGOGiftSpec['tag'],
    keyword: 'Bleed',
    attributeType: 'Red',
    themePack: [],
  },
}

const mockI18n: EGOGiftNameList = {
  '9001': 'Blazing Gift',
  '9002': 'Bleeding Gift',
}

vi.mock('@/hooks/useEGOGiftObservationData', () => ({
  useEGOGiftObservationData: (_version: number) => ({
    data: mockObservationData,
  }),
}))

vi.mock('@/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({
    spec: mockSpec,
    i18n: mockI18n,
  }),
}))

// Mock StarlightCostDisplay
vi.mock('@/components/common/StarlightCostDisplay', () => ({
  StarlightCostDisplay: ({ cost, size }: { cost: number; size: string }) => (
    <div data-testid="starlight-cost" data-cost={cost} data-size={size}>
      Cost: {cost}
    </div>
  ),
}))

// Mock Sorter
vi.mock('@/components/common/Sorter', () => ({
  Sorter: () => <select data-testid="sorter" />,
}))

// Mock EGOGiftKeywordFilter
vi.mock('./EGOGiftKeywordFilter', () => ({
  EGOGiftKeywordFilter: () => <div data-testid="keyword-filter" />,
}))

// Mock SearchBar
vi.mock('@/components/common/SearchBar', () => ({
  SearchBar: () => <input data-testid="search-bar" />,
}))

// Mock EGOGiftSelectionList
vi.mock('./EGOGiftSelectionList', () => ({
  EGOGiftSelectionList: () => <div data-testid="selection-list" data-max="3" />,
}))

// Mock EGOGiftObservationSelection
vi.mock('./EGOGiftObservationSelection', () => ({
  EGOGiftObservationSelection: () => <div data-testid="selected-gifts" />,
}))

describe('EGOGiftObservationEditPane', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
    mdVersion: 6,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockSetObservationGiftIds.mockClear()
  })

  describe('dialog visibility', () => {
    it('renders dialog when open=true', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)

      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getByText('EGO Gift Observation')).toBeInTheDocument()
    })

    it('does not render dialog when open=false', () => {
      render(<EGOGiftObservationEditPane open={false} onOpenChange={vi.fn()} mdVersion={6} />)

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  describe('cost display', () => {
    it('shows cost 0 for empty selection', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)

      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '0')
    })
  })

  describe('filter controls', () => {
    it('renders keyword filter', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('keyword-filter')).toBeInTheDocument()
    })

    it('renders sorter', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('sorter')).toBeInTheDocument()
    })

    it('renders search bar', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('search-bar')).toBeInTheDocument()
    })
  })

  describe('selection list layout', () => {
    it('renders selection list', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('selection-list')).toBeInTheDocument()
    })

    it('renders selected gifts', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByTestId('selected-gifts')).toBeInTheDocument()
    })
  })

  describe('Done button', () => {
    it('renders Done button', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByRole('button', { name: 'Done' })).toBeInTheDocument()
    })

    it('calls onOpenChange(false) when Done is clicked', async () => {
      const onOpenChange = vi.fn()
      const user = userEvent.setup()

      render(<EGOGiftObservationEditPane open onOpenChange={onOpenChange} />)
      await user.click(screen.getByRole('button', { name: 'Done' }))

      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })

  describe('Reset button', () => {
    it('renders Reset button', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)
      expect(screen.getByRole('button', { name: 'Reset' })).toBeInTheDocument()
    })

    it('calls setObservationGiftIds with empty Set when Reset is clicked', async () => {
      const user = userEvent.setup()

      render(<EGOGiftObservationEditPane {...defaultProps} />)
      await user.click(screen.getByRole('button', { name: 'Reset' }))

      expect(mockSetObservationGiftIds).toHaveBeenCalledWith(new Set())
    })
  })
})
