/**
 * StartGiftEditPane.test.tsx
 *
 * Unit tests for StartGiftEditPane component.
 * Tests dialog visibility, row rendering, EA counter, and Done button.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StartGiftEditPane } from '../StartGiftEditPane'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        const translations: Record<string, string> = {
          'pages.plannerMD.startEgoGift': 'Start EGO Gift',
          'pages.plannerMD.egoGiftSelection': 'EA',
          'common:done': 'Done',
        }
        return translations[key] ?? key
      },
      i18n: { language: 'EN' },
    }),
  }
})

// Mock planner editor store
const mockSetSelectedGiftKeyword = vi.fn()
const mockSetSelectedGiftIds = vi.fn()
let mockSelectedBuffIds = new Set<number>()
let mockSelectedKeyword: string | null = null
let mockSelectedGiftIds = new Set<string>()

vi.mock('@/stores/usePlannerEditorStore', () => ({
  usePlannerEditorStore: (selector: (state: Record<string, unknown>) => unknown) => {
    const mockState = {
      selectedBuffIds: mockSelectedBuffIds,
      selectedGiftKeyword: mockSelectedKeyword,
      selectedGiftIds: mockSelectedGiftIds,
      setSelectedGiftKeyword: mockSetSelectedGiftKeyword,
      setSelectedGiftIds: mockSetSelectedGiftIds,
    }
    return selector(mockState)
  },
}))

const mockPools: Record<string, number[]> = {
  Burn: [9001, 9002, 9003],
  Bleed: [9004, 9005, 9006],
}

const mockSpec: Record<string, EGOGiftSpec> = {
  '9001': {
    tag: ['TIER_1'] as EGOGiftSpec['tag'],
    keyword: 'Burn',
    attributeType: 'Red',
    themePack: [],
  },
  '9002': {
    tag: ['TIER_2'] as EGOGiftSpec['tag'],
    keyword: 'Burn',
    attributeType: 'Red',
    themePack: [],
  },
  '9003': {
    tag: ['TIER_3'] as EGOGiftSpec['tag'],
    keyword: 'Burn',
    attributeType: 'Red',
    themePack: [],
  },
}

const mockI18n: EGOGiftNameList = {
  '9001': 'Burning Gift 1',
  '9002': 'Burning Gift 2',
  '9003': 'Burning Gift 3',
}

vi.mock('@/hooks/useStartBuffData', () => ({
  useStartBuffData: () => ({
    data: [
      { id: '100', baseId: 100, level: 1, name: 'Base Buff', cost: 0, effects: [] },
      { id: '200', baseId: 100, level: 2, name: 'Enhanced Buff', cost: 10, effects: [{ type: 'ADDITIONAL_START_EGO_GIFT_SELECT', value: 1, isTypoExist: false }] },
    ],
    i18n: {},
  }),
}))

vi.mock('@/hooks/useStartGiftPools', () => ({
  useStartGiftPools: () => ({
    data: mockPools,
  }),
}))

vi.mock('@/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({
    spec: mockSpec,
    i18n: mockI18n,
  }),
}))

vi.mock('@/lib/startGiftCalculator', () => ({
  calculateMaxGiftSelection: (buffs: unknown[], selectedBuffIds: Set<number>) => {
    // Return 2 if buff 200 is selected, else 1
    return selectedBuffIds.has(200) ? 2 : 1
  },
}))

// Mock StartGiftRow to simplify testing
vi.mock('../StartGiftRow', () => ({
  StartGiftRow: ({
    keyword,
    isRowSelected,
    onRowSelect,
  }: {
    keyword: string
    isRowSelected: boolean
    onRowSelect: (keyword: string) => void
  }) => (
    <button
      type="button"
      data-testid={`row-${keyword}`}
      data-selected={isRowSelected}
      onClick={() => { onRowSelect(keyword); }}
    >
      {keyword}
    </button>
  ),
}))

describe('StartGiftEditPane', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
    mdVersion: 'MD6' as const,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockSelectedBuffIds = new Set<number>()
    mockSelectedKeyword = null
    mockSelectedGiftIds = new Set<string>()
  })

  describe('dialog visibility (UT5)', () => {
    it('renders dialog when open=true', () => {
      render(<StartGiftEditPane {...defaultProps} open />)

      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getByText('Start EGO Gift')).toBeInTheDocument()
    })

    it('does not render dialog when open=false', () => {
      render(<StartGiftEditPane {...defaultProps} open={false} />)

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  describe('row rendering (UT6)', () => {
    it('renders keyword rows from pools', () => {
      render(<StartGiftEditPane {...defaultProps} />)

      expect(screen.getByTestId('row-Burn')).toBeInTheDocument()
      expect(screen.getByTestId('row-Bleed')).toBeInTheDocument()
    })

    it('marks selected keyword row', () => {
      mockSelectedKeyword = 'Burn'

      render(<StartGiftEditPane {...defaultProps} />)

      expect(screen.getByTestId('row-Burn')).toHaveAttribute('data-selected', 'true')
      expect(screen.getByTestId('row-Bleed')).toHaveAttribute('data-selected', 'false')
    })
  })

  describe('Done button (UT7)', () => {
    it('renders Done button', () => {
      render(<StartGiftEditPane {...defaultProps} />)

      expect(screen.getByRole('button', { name: 'Done' })).toBeInTheDocument()
    })

    it('calls onOpenChange(false) when Done is clicked', async () => {
      const onOpenChange = vi.fn()
      const user = userEvent.setup()

      render(<StartGiftEditPane {...defaultProps} onOpenChange={onOpenChange} />)

      await user.click(screen.getByRole('button', { name: 'Done' }))

      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })

  describe('EA counter', () => {
    it('shows EA counter with correct format', () => {
      mockSelectedGiftIds = new Set(['9001'])

      render(<StartGiftEditPane {...defaultProps} />)

      // EA counter is in format "EA: X/Y"
      expect(screen.getByText(/1\/1/)).toBeInTheDocument()
    })

    it('shows increased max when buff with ADDITIONAL_START_EGO_GIFT_SELECT is selected', () => {
      mockSelectedBuffIds = new Set([200])
      mockSelectedGiftIds = new Set(['9001'])

      render(<StartGiftEditPane {...defaultProps} />)

      // Base 1 + 1 from buff effect = 2
      expect(screen.getByText(/1\/2/)).toBeInTheDocument()
    })
  })

  describe('keyword selection', () => {
    it('calls setSelectedGiftKeyword when row is clicked', async () => {
      const user = userEvent.setup()

      render(<StartGiftEditPane {...defaultProps} />)

      await user.click(screen.getByTestId('row-Burn'))

      expect(mockSetSelectedGiftKeyword).toHaveBeenCalledWith('Burn')
    })
  })
})
