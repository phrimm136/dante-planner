import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import type { StartBuffDataList } from '@/shared/gameText'
import { StartBuffEditPane } from '../StartBuffEditPane'

const ELEVEN_BASE_IDS = [100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110]

const ELEVEN_BUFF_SEASON: StartBuffDataList = Object.fromEntries(
  ELEVEN_BASE_IDS.map((id) => [
    String(id),
    {
      level: 1,
      baseId: id,
      cost: 10,
      localizeId: `buff_${String(id)}`,
      effects: [],
      uiConfig: { iconSpriteId: `StartBuffIcon_${String(id)}` },
    },
  ]),
)

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

const mockSetSelectedBuffIds = vi.fn()
vi.mock('../../../stores/usePlannerEditorStore', () => ({
  usePlannerEditorStore: (selector: (state: Record<string, unknown>) => unknown) =>
    selector({ selectedBuffIds: new Set<number>(), setSelectedBuffIds: mockSetSelectedBuffIds }),
}))

vi.mock('../../../hooks/useStartBuffList', () => ({
  useStartBuffListSpec: () => ELEVEN_BUFF_SEASON,
  useStartBuffListI18n: () => ({}),
}))

vi.mock('@/shared/gameText/hooks/useBattleKeywords', () => ({
  useBattleKeywords: () => ({ data: {} }),
}))

vi.mock('../../../hooks/useStartBuffCardLayout', async () => {
  const { StartBuffCardLayoutSchema } = await import('../../../schemas/StartBuffCardSchemas')
  const md7Card = await import('@static/data/MD7/startBuffCard.json')
  const layout = StartBuffCardLayoutSchema.parse(md7Card.default)
  return { useStartBuffCardLayout: () => layout }
})

vi.mock('../StartBuffCard', () => ({
  StartBuffCard: () => <div data-testid="start-buff-card" />,
}))

describe('StartBuffEditPane with a season of eleven base buffs', () => {
  it('shows a card for each of the 11 base buffs', () => {
    render(<StartBuffEditPane open onOpenChange={vi.fn()} mdVersion={7} />)

    expect(screen.getAllByTestId('start-buff-card')).toHaveLength(11)
  })

  it('selects all 11 base buffs', () => {
    render(<StartBuffEditPane open onOpenChange={vi.fn()} mdVersion={7} />)

    fireEvent.click(screen.getByRole('button', { name: 'common:selectAll' }))

    expect(mockSetSelectedBuffIds).toHaveBeenCalledWith(new Set(ELEVEN_BASE_IDS))
  })
})
