import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import type { ReactNode } from 'react'
import type { StoreApi } from 'zustand'
import type { StartBuffDataList } from '@/shared/gameText'
import { StartBuffEditPane } from '../StartBuffEditPane'
import { usePlannerStoreApi } from '../../../stores/usePlannerStore'
import type { PlannerStore } from '../../../stores/usePlannerStore'
import { SeededPlannerStore } from '../../../stores/__tests__/plannerStoreHarness'

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

function StoreProbe({ onStore }: { onStore: (store: StoreApi<PlannerStore>) => void }) {
  onStore(usePlannerStoreApi())
  return null
}

function renderInSeason(ui: ReactNode) {
  let store: StoreApi<PlannerStore> | undefined
  render(
    <SeededPlannerStore mdVersion={7}>
      {ui}
      <StoreProbe
        onStore={(api) => {
          store = api
        }}
      />
    </SeededPlannerStore>,
  )
  return () => {
    if (store === undefined) throw new Error('the planner store never mounted')
    return store
  }
}

describe('StartBuffEditPane with a season of eleven base buffs', () => {
  it('shows a card for each of the 11 base buffs', () => {
    renderInSeason(<StartBuffEditPane open onOpenChange={vi.fn()} />)

    expect(screen.getAllByTestId('start-buff-card')).toHaveLength(11)
  })

  it('selects all 11 base buffs', () => {
    const getStore = renderInSeason(<StartBuffEditPane open onOpenChange={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'common:selectAll' }))

    expect(getStore().getState().selectedBuffIds).toEqual(new Set(ELEVEN_BASE_IDS))
  })
})
