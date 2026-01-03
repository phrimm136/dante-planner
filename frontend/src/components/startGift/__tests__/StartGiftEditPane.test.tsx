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
import type { StartBuff } from '@/types/StartBuffTypes'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'pages.plannerMD.startGift': 'Start Gift',
        'pages.plannerMD.giftSelection': 'EA',
        'common.done': 'Done',
      }
      return translations[key] ?? key
    },
    i18n: { language: 'EN' },
  }),
}))

// Mock hooks
const mockBuffs: StartBuff[] = [
  {
    id: '100',
    baseId: 100,
    level: 1,
    name: 'Base Buff',
    cost: 0,
    effects: [],
    iconSpriteId: 'icon100',
  },
  {
    id: '200',
    baseId: 100,
    level: 2,
    name: 'Enhanced Buff',
    cost: 10,
    effects: [{ type: 'ADDITIONAL_START_EGO_GIFT_SELECT', value: 1, isTypoExist: false }],
    iconSpriteId: 'icon200',
  },
]

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
    data: mockBuffs,
    i18n: {},
  }),
  getBuffById: (buffs: StartBuff[] | undefined, id: number) =>
    buffs?.find((b) => b.id === String(id)),
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
    mdVersion: 6 as const,
    selectedBuffIds: new Set<number>(),
    selectedKeyword: null,
    selectedGiftIds: new Set<string>(),
    onKeywordChange: vi.fn(),
    onGiftSelectionChange: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('dialog visibility (UT5)', () => {
    it('renders dialog when open=true', () => {
      render(<StartGiftEditPane {...defaultProps} open />)

      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getByText('Start Gift')).toBeInTheDocument()
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
      const props = {
        ...defaultProps,
        selectedKeyword: 'Burn',
      }

      render(<StartGiftEditPane {...props} />)

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
      const props = {
        ...defaultProps,
        selectedGiftIds: new Set(['9001']),
      }

      render(<StartGiftEditPane {...props} />)

      // EA counter is in format "EA: X/Y"
      expect(screen.getByText(/1\/1/)).toBeInTheDocument()
    })

    it('shows increased max when buff with ADDITIONAL_START_EGO_GIFT_SELECT is selected', () => {
      const props = {
        ...defaultProps,
        selectedBuffIds: new Set([200]),
        selectedGiftIds: new Set(['9001']),
      }

      render(<StartGiftEditPane {...props} />)

      // Base 1 + 1 from buff effect = 2
      expect(screen.getByText(/1\/2/)).toBeInTheDocument()
    })
  })

  describe('keyword selection', () => {
    it('calls onKeywordChange when row is clicked', async () => {
      const onKeywordChange = vi.fn()
      const user = userEvent.setup()

      render(<StartGiftEditPane {...defaultProps} onKeywordChange={onKeywordChange} />)

      await user.click(screen.getByTestId('row-Burn'))

      expect(onKeywordChange).toHaveBeenCalledWith('Burn')
    })

    it('deselects keyword and clears gifts when same row clicked again', async () => {
      const onKeywordChange = vi.fn()
      const onGiftSelectionChange = vi.fn()
      const user = userEvent.setup()

      const props = {
        ...defaultProps,
        selectedKeyword: 'Burn',
        onKeywordChange,
        onGiftSelectionChange,
      }

      render(<StartGiftEditPane {...props} />)

      // Click the already-selected row to deselect
      await user.click(screen.getByTestId('row-Burn'))

      expect(onKeywordChange).toHaveBeenCalledWith(null)
      expect(onGiftSelectionChange).toHaveBeenCalledWith(new Set())
    })
  })

  describe('edge cases', () => {
    it('trims excess gifts when EA decreases', async () => {
      const onGiftSelectionChange = vi.fn()

      // Start with 2 gifts selected (requires EA buff)
      const props = {
        ...defaultProps,
        selectedBuffIds: new Set([200]), // +1 EA buff
        selectedGiftIds: new Set(['9001', '9002']), // 2 gifts
        onGiftSelectionChange,
      }

      const { rerender } = render(<StartGiftEditPane {...props} />)

      // Now remove the EA buff - should trigger trimming
      const newProps = {
        ...props,
        selectedBuffIds: new Set<number>(), // Remove buff, EA back to 1
      }

      rerender(<StartGiftEditPane {...newProps} />)

      // Should trim to 1 gift
      await waitFor(() => {
        expect(onGiftSelectionChange).toHaveBeenCalled()
      })
    })
  })
})
