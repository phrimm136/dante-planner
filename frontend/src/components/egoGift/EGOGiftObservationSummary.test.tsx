/**
 * EGOGiftObservationSummary.test.tsx
 *
 * Unit tests for EGOGiftObservationSummary component.
 * Tests empty state, selected state, cost display, and accessibility.
 * Note: Component can use store or selectedGiftIdsOverride prop.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EGOGiftObservationSummary } from './EGOGiftObservationSummary'
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
        }
        return translations[key] ?? key
      },
      i18n: { language: 'EN' },
    }),
  }
})

// Mock planner editor store safe (returns empty Set for testing)
vi.mock('@/stores/usePlannerEditorStore', () => ({
  usePlannerEditorStoreSafe: () => new Set<string>(),
}))

// Mock observation data (cost lookup)
const mockObservationData = {
  observationEgoGiftCostDataList: [
    { egogiftCount: 0, starlightCost: 0 },
    { egogiftCount: 1, starlightCost: 70 },
    { egogiftCount: 2, starlightCost: 160 },
    { egogiftCount: 3, starlightCost: 270 },
  ],
  observationEgoGiftDataList: ['9001', '9002', '9003'],
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
}

const mockI18n: EGOGiftNameList = {
  '9001': 'Blazing Gift',
  '9002': 'Bleeding Gift',
  '9003': 'Tremor Gift',
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

// Mock PlannerSection to simplify testing
vi.mock('@/components/common/PlannerSection', () => ({
  PlannerSection: ({ title, children }: { title: string; children: React.ReactNode }) => (
    <section data-testid="planner-section">
      <h2>{title}</h2>
      <div>{children}</div>
    </section>
  ),
}))

// Mock StarlightCostDisplay to verify cost value
vi.mock('@/components/common/StarlightCostDisplay', () => ({
  StarlightCostDisplay: ({ cost, size }: { cost: number; size: string }) => (
    <div data-testid="starlight-cost" data-cost={cost} data-size={size}>
      {cost}
    </div>
  ),
}))

// Mock EGOGiftCard to simplify testing
vi.mock('./EGOGiftCard', () => ({
  EGOGiftCard: ({ gift, isSelected }: { gift: { id: string; name: string }; isSelected?: boolean }) => (
    <div data-testid={`gift-card-${gift.id}`} data-selected={isSelected}>
      {gift.name}
    </div>
  ),
}))

describe('EGOGiftObservationSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('empty state', () => {
    it('renders placeholder text when no gifts selected', () => {
      render(<EGOGiftObservationSummary />)
      expect(screen.getByText('Select EGO Gifts')).toBeInTheDocument()
    })

    it('renders section title', () => {
      render(<EGOGiftObservationSummary />)
      expect(screen.getByText('EGO Gift Observation')).toBeInTheDocument()
    })

    it('shows cost as 0 when no selection', () => {
      render(<EGOGiftObservationSummary />)
      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '0')
    })
  })

  describe('cost display with override', () => {
    it('shows cost 70 for 1 gift', () => {
      render(<EGOGiftObservationSummary selectedGiftIdsOverride={new Set(['9001'])} />)
      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '70')
    })

    it('shows cost 160 for 2 gifts', () => {
      render(<EGOGiftObservationSummary selectedGiftIdsOverride={new Set(['9001', '9002'])} />)
      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '160')
    })

    it('shows cost 270 for 3 gifts', () => {
      render(<EGOGiftObservationSummary selectedGiftIdsOverride={new Set(['9001', '9002', '9003'])} />)
      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '270')
    })
  })

  describe('selected state with override', () => {
    it('renders gift cards when gifts are selected', () => {
      render(<EGOGiftObservationSummary selectedGiftIdsOverride={new Set(['9001'])} />)
      expect(screen.getByTestId('gift-card-9001')).toBeInTheDocument()
      expect(screen.getByText('Blazing Gift')).toBeInTheDocument()
    })

    it('renders multiple gift cards', () => {
      render(<EGOGiftObservationSummary selectedGiftIdsOverride={new Set(['9001', '9002', '9003'])} />)
      expect(screen.getByTestId('gift-card-9001')).toBeInTheDocument()
      expect(screen.getByTestId('gift-card-9002')).toBeInTheDocument()
      expect(screen.getByTestId('gift-card-9003')).toBeInTheDocument()
    })

    it('does not render placeholder when gifts selected', () => {
      render(<EGOGiftObservationSummary selectedGiftIdsOverride={new Set(['9001'])} />)
      expect(screen.queryByText('Select EGO Gifts')).not.toBeInTheDocument()
    })
  })

  describe('click handling', () => {
    it('calls onClick when clicked', async () => {
      const onClick = vi.fn()
      const user = userEvent.setup()

      render(<EGOGiftObservationSummary onClick={onClick} />)

      const clickableArea = screen.getByRole('button')
      await user.click(clickableArea)

      expect(onClick).toHaveBeenCalledTimes(1)
    })

    it('does not throw when onClick is undefined', async () => {
      const user = userEvent.setup()
      render(<EGOGiftObservationSummary />)
      const clickableArea = screen.getByRole('button')
      // Should not throw
      await user.click(clickableArea)
    })
  })

  describe('edge cases', () => {
    it('gracefully handles unknown gift ID', () => {
      render(<EGOGiftObservationSummary selectedGiftIdsOverride={new Set(['9001', 'unknown-id'])} />)
      // Known gift should render
      expect(screen.getByTestId('gift-card-9001')).toBeInTheDocument()
      // Unknown gift should be skipped (not crash)
      expect(screen.queryByTestId('gift-card-unknown-id')).not.toBeInTheDocument()
    })

    it('defaults to 0 cost for unknown gift count', () => {
      // 4+ gifts beyond cost table should default to 0
      render(<EGOGiftObservationSummary selectedGiftIdsOverride={new Set(['9001', '9002', '9003', 'extra'])} />)
      const costDisplay = screen.getByTestId('starlight-cost')
      expect(costDisplay).toHaveAttribute('data-cost', '0')
    })
  })
})
