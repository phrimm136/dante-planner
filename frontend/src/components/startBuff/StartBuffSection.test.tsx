import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StartBuffSection } from './StartBuffSection'
import { CURRENT_MD_VERSION } from '@/lib/constants'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      if (key === 'pages.plannerMD.startBuffs') return 'Start Buffs'
      if (key === 'pages.plannerMD.selectStartBuffs') return 'Click to select start buffs'
      return key
    },
  }),
}))

// Mock useStartBuffSelection hook
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

// Mock StartBuffMiniCard
vi.mock('./StartBuffMiniCard', () => ({
  StartBuffMiniCard: ({ buffId, displayName }: { buffId: number; displayName: string }) => (
    <div data-testid="start-buff-mini-card" data-buff-id={buffId}>
      {displayName}
    </div>
  ),
}))

// Mock PlannerSection
vi.mock('@/components/common/PlannerSection', () => ({
  PlannerSection: ({ title, children }: { title: string; children: React.ReactNode }) => (
    <section>
      <h2>{title}</h2>
      {children}
    </section>
  ),
}))

describe('StartBuffSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('empty state', () => {
    it('shows placeholder when no buffs selected', () => {
      render(
        <StartBuffSection
          mdVersion={CURRENT_MD_VERSION}
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
        />
      )

      expect(screen.getByText('Click to select start buffs')).toBeDefined()
      expect(screen.queryByTestId('start-buff-mini-card')).toBeNull()
    })

    it('is clickable in empty state', () => {
      const onClick = vi.fn()

      render(
        <StartBuffSection
          mdVersion={CURRENT_MD_VERSION}
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
          onClick={onClick}
        />
      )

      const clickableArea = screen.getByRole('button')
      fireEvent.click(clickableArea)

      expect(onClick).toHaveBeenCalledTimes(1)
    })
  })

  describe('with selected buffs', () => {
    it('renders mini cards for selected buffs', () => {
      render(
        <StartBuffSection
          mdVersion={CURRENT_MD_VERSION}
          selectedBuffIds={new Set([1001])}
          onSelectionChange={() => {}}
        />
      )

      const miniCards = screen.getAllByTestId('start-buff-mini-card')
      expect(miniCards.length).toBe(1)
      expect(miniCards[0].getAttribute('data-buff-id')).toBe('1001')
    })

    it('renders multiple mini cards for multiple selections', () => {
      render(
        <StartBuffSection
          mdVersion={CURRENT_MD_VERSION}
          selectedBuffIds={new Set([1001, 1002])}
          onSelectionChange={() => {}}
        />
      )

      const miniCards = screen.getAllByTestId('start-buff-mini-card')
      expect(miniCards.length).toBe(2)
    })

    it('does not show placeholder when buffs are selected', () => {
      render(
        <StartBuffSection
          mdVersion={CURRENT_MD_VERSION}
          selectedBuffIds={new Set([1001])}
          onSelectionChange={() => {}}
        />
      )

      expect(screen.queryByText('Click to select start buffs')).toBeNull()
    })
  })

  describe('click handling', () => {
    it('calls onClick when section is clicked', () => {
      const onClick = vi.fn()

      render(
        <StartBuffSection
          mdVersion={CURRENT_MD_VERSION}
          selectedBuffIds={new Set([1001])}
          onSelectionChange={() => {}}
          onClick={onClick}
        />
      )

      const clickableArea = screen.getByRole('button')
      fireEvent.click(clickableArea)

      expect(onClick).toHaveBeenCalledTimes(1)
    })

    it('supports keyboard navigation (Enter key)', () => {
      const onClick = vi.fn()

      render(
        <StartBuffSection
          mdVersion={CURRENT_MD_VERSION}
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
          onClick={onClick}
        />
      )

      const clickableArea = screen.getByRole('button')
      fireEvent.keyDown(clickableArea, { key: 'Enter' })

      expect(onClick).toHaveBeenCalledTimes(1)
    })

    it('supports keyboard navigation (Space key)', () => {
      const onClick = vi.fn()

      render(
        <StartBuffSection
          mdVersion={CURRENT_MD_VERSION}
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
          onClick={onClick}
        />
      )

      const clickableArea = screen.getByRole('button')
      fireEvent.keyDown(clickableArea, { key: ' ' })

      expect(onClick).toHaveBeenCalledTimes(1)
    })
  })
})
