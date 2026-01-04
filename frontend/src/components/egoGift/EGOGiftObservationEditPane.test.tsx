/**
 * EGOGiftObservationEditPane.test.tsx
 *
 * Unit tests for EGOGiftObservationEditPane component.
 * Tests dialog visibility, filter controls, selection, and max limit enforcement.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EGOGiftObservationEditPane } from './EGOGiftObservationEditPane'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'pages.plannerMD.egoGiftObservation': 'EGO Gift Observation',
        'pages.plannerMD.selectEgoGifts': 'Select EGO Gifts',
        'pages.plannerMD.selectedEgoGifts': 'Selected EGO Gifts',
        'common.reset': 'Reset',
        'common.done': 'Done',
      }
      return translations[key] ?? key
    },
    i18n: { language: 'EN' },
  }),
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
  '9003': {
    tag: ['TIER_3'] as EGOGiftSpec['tag'],
    keyword: 'Tremor',
    attributeType: 'Yellow',
    themePack: [],
  },
  '9004': {
    tag: ['TIER_1'] as EGOGiftSpec['tag'],
    keyword: 'Rupture',
    attributeType: 'Yellow',
    themePack: [],
  },
}

const mockI18n: EGOGiftNameList = {
  '9001': 'Blazing Gift',
  '9002': 'Bleeding Gift',
  '9003': 'Tremor Gift',
  '9004': 'Rupture Gift',
}

vi.mock('@/hooks/useEGOGiftObservationData', () => ({
  useEGOGiftObservationData: () => ({
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
  Sorter: ({
    sortMode,
    onSortModeChange,
  }: {
    sortMode: string
    onSortModeChange: (mode: string) => void
  }) => (
    <select
      data-testid="sorter"
      value={sortMode}
      onChange={(e) => onSortModeChange(e.target.value)}
    >
      <option value="tier-first">Tier First</option>
      <option value="name">Name</option>
    </select>
  ),
}))

// Mock EGOGiftKeywordFilter
vi.mock('./EGOGiftKeywordFilter', () => ({
  EGOGiftKeywordFilter: ({
    selectedKeywords,
    onSelectionChange,
  }: {
    selectedKeywords: Set<string>
    onSelectionChange: (keywords: Set<string>) => void
  }) => (
    <div data-testid="keyword-filter">
      <button
        type="button"
        onClick={() => onSelectionChange(new Set(['Burn']))}
        data-active={selectedKeywords.has('Burn')}
      >
        Burn Filter
      </button>
    </div>
  ),
}))

// Mock EGOGiftSearchBar
vi.mock('./EGOGiftSearchBar', () => ({
  EGOGiftSearchBar: ({
    searchQuery,
    onSearchChange,
  }: {
    searchQuery: string
    onSearchChange: (query: string) => void
  }) => (
    <input
      data-testid="search-bar"
      value={searchQuery}
      onChange={(e) => onSearchChange(e.target.value)}
      placeholder="Search..."
    />
  ),
}))

// Mock EGOGiftSelectionList
vi.mock('./EGOGiftSelectionList', () => ({
  EGOGiftSelectionList: ({
    gifts,
    selectedGiftIds,
    maxSelectable,
    onGiftSelect,
  }: {
    gifts: Array<{ id: string; name: string }>
    selectedGiftIds: Set<string>
    maxSelectable: number
    onGiftSelect: (id: string) => void
  }) => (
    <div data-testid="selection-list" data-max={maxSelectable}>
      {gifts.map((gift) => (
        <button
          key={gift.id}
          type="button"
          data-testid={`gift-${gift.id}`}
          data-selected={selectedGiftIds.has(gift.id)}
          onClick={() => onGiftSelect(gift.id)}
        >
          {gift.name}
        </button>
      ))}
    </div>
  ),
}))

// Mock EGOGiftObservationSelection
vi.mock('./EGOGiftObservationSelection', () => ({
  EGOGiftObservationSelection: ({
    selectedGiftIds,
    onGiftRemove,
  }: {
    selectedGiftIds: string[]
    onGiftRemove: (id: string) => void
  }) => (
    <div data-testid="selected-gifts">
      {selectedGiftIds.map((id) => (
        <button
          key={id}
          type="button"
          data-testid={`selected-${id}`}
          onClick={() => onGiftRemove(id)}
        >
          Remove {id}
        </button>
      ))}
    </div>
  ),
}))

describe('EGOGiftObservationEditPane', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
    selectedGiftIds: new Set<string>(),
    onSelectionChange: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('dialog visibility (F5)', () => {
    it('renders dialog when open=true', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} open />)

      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getByText('EGO Gift Observation')).toBeInTheDocument()
    })

    it('does not render dialog when open=false', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} open={false} />)

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  describe('cost display (F6)', () => {
    it('shows cost 0 for 0 gifts', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)

      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '0')
    })

    it('shows cost 70 for 1 gift', () => {
      const props = {
        ...defaultProps,
        selectedGiftIds: new Set(['9001']),
      }

      render(<EGOGiftObservationEditPane {...props} />)

      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '70')
    })

    it('shows cost 270 for 3 gifts', () => {
      const props = {
        ...defaultProps,
        selectedGiftIds: new Set(['9001', '9002', '9003']),
      }

      render(<EGOGiftObservationEditPane {...props} />)

      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '270')
    })
  })

  describe('filter controls (F7)', () => {
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

  describe('selection list layout (F8, F9)', () => {
    it('renders selection list section', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)

      expect(screen.getByText('Select EGO Gifts')).toBeInTheDocument()
      expect(screen.getByTestId('selection-list')).toBeInTheDocument()
    })

    it('renders selected gifts section', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)

      expect(screen.getByText('Selected EGO Gifts')).toBeInTheDocument()
      expect(screen.getByTestId('selected-gifts')).toBeInTheDocument()
    })

    it('passes maxSelectable=3 to selection list', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)

      const selectionList = screen.getByTestId('selection-list')
      expect(selectionList).toHaveAttribute('data-max', '3')
    })
  })

  describe('max selection enforcement (F10)', () => {
    it('adds gift when under limit', async () => {
      const onSelectionChange = vi.fn()
      const user = userEvent.setup()

      render(
        <EGOGiftObservationEditPane
          {...defaultProps}
          selectedGiftIds={new Set(['9001', '9002'])} // 2 selected
          onSelectionChange={onSelectionChange}
        />
      )

      await user.click(screen.getByTestId('gift-9003'))

      // Should add the gift (3 total, at limit)
      expect(onSelectionChange).toHaveBeenCalledWith(new Set(['9001', '9002', '9003']))
    })

    it('blocks 4th selection when at limit', async () => {
      const onSelectionChange = vi.fn()
      const user = userEvent.setup()

      render(
        <EGOGiftObservationEditPane
          {...defaultProps}
          selectedGiftIds={new Set(['9001', '9002', '9003'])} // 3 selected (at limit)
          onSelectionChange={onSelectionChange}
        />
      )

      await user.click(screen.getByTestId('gift-9004'))

      // Should NOT add the gift - still has 3
      expect(onSelectionChange).toHaveBeenCalledWith(new Set(['9001', '9002', '9003']))
    })

    it('allows deselection when at limit', async () => {
      const onSelectionChange = vi.fn()
      const user = userEvent.setup()

      render(
        <EGOGiftObservationEditPane
          {...defaultProps}
          selectedGiftIds={new Set(['9001', '9002', '9003'])}
          onSelectionChange={onSelectionChange}
        />
      )

      // Click already-selected gift to deselect
      await user.click(screen.getByTestId('gift-9001'))

      expect(onSelectionChange).toHaveBeenCalledWith(new Set(['9002', '9003']))
    })
  })

  describe('filter state reset (F11)', () => {
    it('resets filters when dialog closes', async () => {
      const { rerender } = render(
        <EGOGiftObservationEditPane {...defaultProps} open />
      )

      // Change filter values
      const searchBar = screen.getByTestId('search-bar')
      await userEvent.type(searchBar, 'test')
      expect(searchBar).toHaveValue('test')

      // Close dialog
      rerender(<EGOGiftObservationEditPane {...defaultProps} open={false} />)

      // Reopen dialog
      rerender(<EGOGiftObservationEditPane {...defaultProps} open />)

      // Filters should be reset
      await waitFor(() => {
        expect(screen.getByTestId('search-bar')).toHaveValue('')
      })
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

      render(<EGOGiftObservationEditPane {...defaultProps} onOpenChange={onOpenChange} />)

      await user.click(screen.getByRole('button', { name: 'Done' }))

      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })

  describe('Reset button', () => {
    it('renders Reset button', () => {
      render(<EGOGiftObservationEditPane {...defaultProps} />)

      expect(screen.getByRole('button', { name: 'Reset' })).toBeInTheDocument()
    })

    it('clears selection when Reset is clicked', async () => {
      const onSelectionChange = vi.fn()
      const user = userEvent.setup()

      render(
        <EGOGiftObservationEditPane
          {...defaultProps}
          selectedGiftIds={new Set(['9001', '9002'])}
          onSelectionChange={onSelectionChange}
        />
      )

      await user.click(screen.getByRole('button', { name: 'Reset' }))

      expect(onSelectionChange).toHaveBeenCalledWith(new Set())
    })
  })

  describe('gift removal', () => {
    it('removes gift when clicked in selected list', async () => {
      const onSelectionChange = vi.fn()
      const user = userEvent.setup()

      render(
        <EGOGiftObservationEditPane
          {...defaultProps}
          selectedGiftIds={new Set(['9001', '9002'])}
          onSelectionChange={onSelectionChange}
        />
      )

      await user.click(screen.getByTestId('selected-9001'))

      expect(onSelectionChange).toHaveBeenCalledWith(new Set(['9002']))
    })
  })

  describe('edge cases', () => {
    it('defaults to 0 cost for unknown gift count', () => {
      // This tests the fallback when cost lookup fails
      const props = {
        ...defaultProps,
        selectedGiftIds: new Set(['9001', '9002', '9003', '9004', '9005']), // 5 gifts (beyond cost table)
      }

      render(<EGOGiftObservationEditPane {...props} />)

      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '0')
    })
  })
})
