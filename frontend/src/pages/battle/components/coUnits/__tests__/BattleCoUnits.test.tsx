import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import {
  RouterProvider,
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  useParams,
  useSearch,
} from '@tanstack/react-router'

import enemy1137 from '@static/data/enemy/1137.json'
import enemy8193 from '@static/data/enemy/8193.json'
import enemy8556 from '@static/data/enemy/8556.json'
import enemy8564 from '@static/data/enemy/8564.json'
import i18n from '@/lib/i18n'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { EnemyDataSchema, type EngagementRow } from '../../../schemas/BattleDetailSchemas'
import { BattleCoUnits } from '../BattleCoUnits'

const LOAD_WAIT_MS = 10000

function rowOf(raw: unknown, unit: number, stageId: string): EngagementRow {
  const row = EnemyDataSchema.parse(raw).engagements.find(
    (each) => each.unit === unit && each.stage.endsWith(`#${stageId}`),
  )
  if (row === undefined) throw new Error(`no ${String(unit)} row on ${stageId}`)
  return row
}

function mountChips(row: EngagementRow, id: string) {
  const rootRoute = createRootRoute()
  const detailRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/battle/enemy/$id',
    component: function Detail() {
      const params = useParams({ strict: false })
      const search: { stage?: string } = useSearch({ strict: false })
      return (
        <>
          <output data-testid="location">{`${String(params.id)} ${String(search.stage)}`}</output>
          {params.id === id && <BattleCoUnits row={row} family="enemy" id={id} />}
        </>
      )
    },
  })
  const router = createRouter({
    routeTree: rootRoute.addChildren([detailRoute]),
    history: createMemoryHistory({ initialEntries: [`/battle/enemy/${id}`] }),
    defaultPendingMs: 0,
  })
  const queryClient = createTestQueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return queryClient
}

async function chipLabels(): Promise<string[]> {
  const line = await screen.findByTestId('battle-co-units', undefined, { timeout: LOAD_WAIT_MS })
  return within(line)
    .getAllByRole('link')
    .map((link) => link.textContent ?? '')
}

beforeEach(async () => {
  await i18n.changeLanguage('KR')
})

describe('BattleCoUnits', () => {
  it('marks the roses on the 장미 표지판 card as its summons', async () => {
    mountChips(rowOf(enemy8556, 1026, '2067035'), '8556')
    const labels = await chipLabels()
    expect(labels).toContain('분노를 머금은 장미 (소환)')
    expect(labels).toContain('색욕을 머금은 장미 (소환)')
    expect(screen.getByText('함께 등장하는 유닛')).toBeDefined()
  })

  it('marks 장미 표지판 as the summoner on the rose card', async () => {
    mountChips(rowOf(enemy8564, 1027, '2067035'), '8564')
    expect(await chipLabels()).toContain('장미 표지판 (소환자)')
  })

  it('lists 자로 and the two 흑수 on the 가치우 card with no relation mark', async () => {
    mountChips(rowOf(enemy1137, 1137, '10827'), '1137')
    expect(await chipLabels()).toEqual(['자로', '흑수 - 묘 : 쾌격', '흑수 - 묘 : 암격'])
  })

  it('uses the language of the page for the line and the marks', async () => {
    await i18n.changeLanguage('EN')
    mountChips(rowOf(enemy8564, 1027, '2067035'), '8564')
    const labels = await chipLabels()
    expect(labels.some((label) => label.endsWith(' (summoner)'))).toBe(true)
    expect(screen.getByText('Also appearing')).toBeDefined()
  })

  it('leaves out the chip of the current card', async () => {
    mountChips(rowOf(enemy8193, 8193, '10648'), '8193')
    const line = await screen.findByTestId('battle-co-units', undefined, { timeout: LOAD_WAIT_MS })
    const hrefs = within(line)
      .getAllByRole('link')
      .map((link) => link.getAttribute('href') ?? '')
    expect(hrefs.some((href) => href.startsWith('/battle/enemy/8192?'))).toBe(true)
    expect(hrefs.some((href) => href.startsWith('/battle/enemy/8193?'))).toBe(false)
  })

  it('opens the other card on the same stage', async () => {
    const row = rowOf(enemy1137, 1137, '10827')
    mountChips(row, '1137')
    const line = await screen.findByTestId('battle-co-units', undefined, { timeout: LOAD_WAIT_MS })
    fireEvent.click(within(line).getByRole('link', { name: '자로' }))
    await waitFor(() => {
      expect(screen.getByTestId('location').textContent).toBe(`1126 ${row.stage}`)
    })
  })

  it('renders nothing on a row without co-units', () => {
    mountChips({ ...rowOf(enemy1137, 1137, '10827'), coUnits: [] }, '1137')
    expect(screen.queryByTestId('battle-co-units')).toBeNull()
    expect(screen.queryByText('함께 등장하는 유닛')).toBeNull()
  })

  it('renders nothing when every co-unit is the current card', async () => {
    const row = rowOf(enemy8193, 8193, '10648')
    const own = row.coUnits.filter(({ card }) => card.id === 8193)
    const queryClient = mountChips({ ...row, coUnits: own }, '8193')
    await waitFor(
      () => {
        const queries = queryClient.getQueryCache().getAll()
        expect(queries.length).toBeGreaterThan(0)
        expect(queries.every((query) => query.state.status === 'success')).toBe(true)
        expect(document.querySelectorAll('[data-slot="skeleton"]').length).toBe(0)
      },
      { timeout: LOAD_WAIT_MS },
    )
    expect(screen.queryByTestId('battle-co-units')).toBeNull()
  })
})
