import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StartBuffEditPane } from './StartBuffEditPane'

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        const translations: Record<string, string> = {
          'pages.plannerMD.startBuffs': 'Start Buffs',
          'common:done': 'Done',
          'common:reset': 'Reset',
        }
        return translations[key] ?? key
      },
    }),
  }
})

// Mock planner editor store
const mockSetSelectedBuffIds = vi.fn()
vi.mock('@/stores/usePlannerEditorStore', () => ({
  usePlannerEditorStore: (selector: (state: Record<string, unknown>) => unknown) => {
    const mockState = {
      selectedBuffIds: new Set<number>(),
      setSelectedBuffIds: mockSetSelectedBuffIds,
    }
    return selector(mockState)
  },
}))

// Mock data hooks
vi.mock('@/hooks/useStartBuffSelection', () => ({
  useStartBuffSelection: () => ({
    buffs: [
      { id: '1001', baseId: 1001, cost: 5, name: 'Buff 1', effects: [] },
      { id: '1002', baseId: 1002, cost: 5, name: 'Buff 2', effects: [] },
    ],
    i18n: {},
    battleKeywords: {},
    displayBuffs: [
      { id: '1001', baseId: 1001, cost: 5, name: 'Buff 1', effects: [] },
      { id: '1002', baseId: 1002, cost: 5, name: 'Buff 2', effects: [] },
    ],
    handleSelect: vi.fn(),
  }),
}))

vi.mock('@/hooks/useBattleKeywords', () => ({
  useBattleKeywords: () => ({ data: {} }),
}))

// Mock StartBuffCard
vi.mock('./StartBuffCard', () => ({
  StartBuffCard: () => <div data-testid="start-buff-card">Start Buff Card</div>,
}))

describe('StartBuffEditPane', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
    mdVersion: 'MD6' as const,
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('dialog visibility', () => {
    it('renders dialog content when open=true', () => {
      render(<StartBuffEditPane {...defaultProps} open={true} />)

      expect(screen.getByText('Start Buffs')).toBeDefined()
      expect(screen.getAllByTestId('start-buff-card').length).toBeGreaterThan(0)
      expect(screen.getByText('Done')).toBeDefined()
    })

    it('does not render dialog content when open=false', () => {
      render(<StartBuffEditPane {...defaultProps} open={false} />)

      expect(screen.queryByText('Start Buffs')).toBeNull()
    })
  })

  describe('dialog structure (IT1)', () => {
    it('renders with correct title', () => {
      render(<StartBuffEditPane {...defaultProps} />)

      const title = screen.getByRole('heading', { name: 'Start Buffs' })
      expect(title).toBeDefined()
    })

    it('renders StartBuffCard for each buff', () => {
      render(<StartBuffEditPane {...defaultProps} />)

      const cards = screen.getAllByTestId('start-buff-card')
      expect(cards.length).toBeGreaterThan(0)
    })

    it('renders Done button', () => {
      render(<StartBuffEditPane {...defaultProps} />)

      const doneButton = screen.getByRole('button', { name: 'Done' })
      expect(doneButton).toBeDefined()
    })
  })

  describe('dialog interactions', () => {
    it('calls onOpenChange(false) when Done button clicked', () => {
      const onOpenChange = vi.fn()

      render(<StartBuffEditPane {...defaultProps} onOpenChange={onOpenChange} />)

      const doneButton = screen.getByRole('button', { name: 'Done' })
      fireEvent.click(doneButton)

      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })
})
