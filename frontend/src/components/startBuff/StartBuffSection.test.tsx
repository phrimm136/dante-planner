import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StartBuffSection } from './StartBuffSection'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key === 'pages.plannerMD.startBuffs' ? 'Start Buffs' : key,
  }),
}))

// Mock data hooks
vi.mock('@/hooks/useStartBuffData', () => ({
  useStartBuffData: () => ({
    data: [
      { id: '1001', baseId: 1001, cost: 5, name: 'Buff 1', effects: [] },
      { id: '1002', baseId: 1002, cost: 5, name: 'Buff 2', effects: [] },
    ],
    i18n: {},
  }),
  getBaseBuffs: (buffs: { baseId: number }[]) => buffs,
}))

vi.mock('@/hooks/useBattleKeywords', () => ({
  useBattleKeywords: () => ({ data: {} }),
}))

// Mock StartBuffCard
vi.mock('./StartBuffCard', () => ({
  StartBuffCard: ({ viewMode }: { viewMode?: boolean }) => (
    <div data-testid="start-buff-card">Card (viewMode={String(viewMode)})</div>
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

  describe('default mode (viewMode=false)', () => {
    it('passes viewMode=false to StartBuffCard', () => {
      render(
        <StartBuffSection
          mdVersion="MD6"
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
        />
      )

      const cards = screen.getAllByTestId('start-buff-card')
      expect(cards[0].textContent).toContain('viewMode=false')
    })

    it('renders cards directly without click wrapper', () => {
      render(
        <StartBuffSection
          mdVersion="MD6"
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
        />
      )

      const cards = screen.getAllByTestId('start-buff-card')
      // Cards should not be wrapped in a clickable div
      expect(cards[0].closest('[role="button"]')).toBeNull()
    })
  })

  describe('view mode (viewMode=true)', () => {
    it('passes viewMode=true to StartBuffCard (UT3)', () => {
      render(
        <StartBuffSection
          mdVersion="MD6"
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
          viewMode={true}
        />
      )

      const cards = screen.getAllByTestId('start-buff-card')
      expect(cards[0].textContent).toContain('viewMode=true')
    })

    it('wraps grid in clickable container', () => {
      render(
        <StartBuffSection
          mdVersion="MD6"
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
          viewMode={true}
          onClick={() => {}}
        />
      )

      const card = screen.getAllByTestId('start-buff-card')[0]
      // Card should be inside a button role element
      expect(card.closest('[role="button"]')).toBeDefined()
    })

    it('calls onClick when section is clicked (UT4)', () => {
      const onClick = vi.fn()

      render(
        <StartBuffSection
          mdVersion="MD6"
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
          viewMode={true}
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
          mdVersion="MD6"
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
          viewMode={true}
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
          mdVersion="MD6"
          selectedBuffIds={new Set()}
          onSelectionChange={() => {}}
          viewMode={true}
          onClick={onClick}
        />
      )

      const clickableArea = screen.getByRole('button')
      fireEvent.keyDown(clickableArea, { key: ' ' })

      expect(onClick).toHaveBeenCalledTimes(1)
    })
  })
})
