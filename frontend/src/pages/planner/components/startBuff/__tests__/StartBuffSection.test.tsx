import { describe, it, expect, vi, beforeEach, assert } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import type { ReactNode } from 'react'
import type { StartBuffDataList } from '@/shared/gameText'
import { StartBuffSection } from '../StartBuffSection'
import { SeededPlannerStore } from '../../../stores/__tests__/plannerStoreHarness'
import { useStartBuffListSpec, useStartBuffListI18n } from '../../../hooks/useStartBuffList'

const SEASON = 6

const SPEC: StartBuffDataList = {
  '101': {
    level: 1,
    baseId: 101,
    cost: 5,
    localizeId: 'buff_101',
    effects: [],
    uiConfig: { iconSpriteId: 'StartBuffIcon_101' },
  },
  '102': {
    level: 1,
    baseId: 102,
    cost: 5,
    localizeId: 'buff_102',
    effects: [],
    uiConfig: { iconSpriteId: 'StartBuffIcon_102' },
  },
}

function InSeason({ children }: { children: ReactNode }) {
  return <SeededPlannerStore mdVersion={SEASON}>{children}</SeededPlannerStore>
}

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        if (key === 'pages.plannerMD.startBuffs') return 'Start Buffs'
        if (key === 'pages.plannerMD.selectStartBuffs') return 'Click to select start buffs'
        return key
      },
    }),
  }
})

vi.mock('../../../hooks/useStartBuffList', () => ({
  useStartBuffListSpec: vi.fn(() => SPEC),
  useStartBuffListI18n: vi.fn(() => ({ buff_101: 'Buff 1', buff_102: 'Buff 2' })),
}))

vi.mock('@/shared/gameText/hooks/useBattleKeywords', () => ({
  useBattleKeywords: () => ({ data: {} }),
}))

// Mock StartBuffMiniCard
vi.mock('../StartBuffMiniCard', () => ({
  StartBuffMiniCard: ({ buffId, displayName }: { buffId: number; displayName: string }) => (
    <div data-testid="start-buff-mini-card" data-buff-id={buffId}>
      {displayName}
    </div>
  ),
}))

// Mock PlannerSection
vi.mock('@/components/layout/PlannerSection', () => ({
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

  describe('season', () => {
    it('loads the buff list of the season in the store', () => {
      render(<StartBuffSection selectedBuffIds={new Set([101])} />, { wrapper: InSeason })

      expect(vi.mocked(useStartBuffListSpec).mock.calls).toEqual([[SEASON]])
      expect(vi.mocked(useStartBuffListI18n).mock.calls).toEqual([[SEASON]])
    })
  })

  describe('empty state', () => {
    it('shows placeholder when no buffs selected', () => {
      render(<StartBuffSection selectedBuffIds={new Set()} />, { wrapper: InSeason })

      expect(screen.getByText('Click to select start buffs')).toBeDefined()
      expect(screen.queryByTestId('start-buff-mini-card')).toBeNull()
    })

    it('is clickable in empty state', () => {
      const onClick = vi.fn()

      render(<StartBuffSection selectedBuffIds={new Set()} onClick={onClick} />, {
        wrapper: InSeason,
      })

      const clickableArea = screen.getByRole('button')
      fireEvent.click(clickableArea)

      expect(onClick).toHaveBeenCalledTimes(1)
    })
  })

  describe('with selected buffs', () => {
    it('renders mini cards for selected buffs', () => {
      render(<StartBuffSection selectedBuffIds={new Set([101])} />, { wrapper: InSeason })

      const miniCards = screen.getAllByTestId('start-buff-mini-card')
      expect(miniCards.length).toBe(1)
      const [firstCard] = miniCards
      assert(firstCard, 'the section renders no mini card')
      expect(firstCard.getAttribute('data-buff-id')).toBe('101')
    })

    it('renders multiple mini cards for multiple selections', () => {
      render(<StartBuffSection selectedBuffIds={new Set([101, 102])} />, { wrapper: InSeason })

      const miniCards = screen.getAllByTestId('start-buff-mini-card')
      expect(miniCards.length).toBe(2)
    })

    it('does not show placeholder when buffs are selected', () => {
      render(<StartBuffSection selectedBuffIds={new Set([101])} />, { wrapper: InSeason })

      expect(screen.queryByText('Click to select start buffs')).toBeNull()
    })
  })

  describe('click handling', () => {
    it('calls onClick when section is clicked', () => {
      const onClick = vi.fn()

      render(<StartBuffSection selectedBuffIds={new Set([101])} onClick={onClick} />, {
        wrapper: InSeason,
      })

      const clickableArea = screen.getByRole('button')
      fireEvent.click(clickableArea)

      expect(onClick).toHaveBeenCalledTimes(1)
    })

    it('uses native button for accessibility (no manual keyboard handling needed)', () => {
      render(<StartBuffSection selectedBuffIds={new Set()} onClick={() => {}} />, {
        wrapper: InSeason,
      })

      const button = screen.getByRole('button')
      expect(button).toHaveAttribute('type', 'button')
      expect(button.tagName).toBe('BUTTON')
    })
  })
})
