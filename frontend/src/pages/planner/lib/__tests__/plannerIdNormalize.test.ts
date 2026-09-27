import { describe, it, expect } from 'vitest'
import { normalizePlannerIds, withNormalizedIds } from '../plannerIdNormalize'
import { EMPTY_ID_MIGRATION_TABLE } from '../idMigrationTable'
import { buildSaveablePlanner } from '@/test-utils/fixtures'
import type { IdMigrationTable } from '../idMigrationTable'

const TABLE: IdMigrationTable = {
  identity: { rename: { '10199': '10101' }, drop: [] },
  ego: { rename: { '20199': '20101' }, drop: ['20299'] },
  egoGift: { rename: { '9247': '9300' }, drop: ['9666'] },
  themePack: { rename: { '1099': '1001' }, drop: ['1098'] },
  startBuff: { rename: { '109': '108' }, drop: ['209'] },
}

function staleContent(): Record<string, unknown> {
  return {
    selectedKeywords: ['Combustion'],
    selectedBuffIds: [100, 109, 209],
    selectedGiftKeyword: 'Combustion',
    selectedGiftIds: ['9247', '9001'],
    observationGiftIds: ['19247', '9666'],
    comprehensiveGiftIds: ['29247', '9300'],
    equipment: {
      '01': {
        identity: { id: '10199', uptie: 3, level: 45 },
        egos: { ZAYIN: { id: '20199', threadspin: 2 }, TETH: { id: '20299', threadspin: 1 } },
      },
      '02': {
        identity: { id: '10201', uptie: 4, level: 50 },
        egos: { ZAYIN: { id: '20201', threadspin: 1 } },
      },
    },
    deploymentOrder: [0, 1],
    skillEAState: {},
    floorSelections: [
      { themePackId: '1099', difficulty: 1, giftIds: ['9247', '19666'] },
      { themePackId: '1098', difficulty: 1, giftIds: [] },
      { themePackId: null, difficulty: 1, giftIds: [] },
    ],
    sectionNotes: {},
  }
}

describe('normalizePlannerIds', () => {
  it('rewrites renamed ids and drops retired ones in every id-bearing field', () => {
    expect(normalizePlannerIds(staleContent(), TABLE)).toEqual({
      selectedKeywords: ['Combustion'],
      selectedBuffIds: [100, 108],
      selectedGiftKeyword: 'Combustion',
      selectedGiftIds: ['9300', '9001'],
      observationGiftIds: ['19300'],
      comprehensiveGiftIds: ['29300', '9300'],
      equipment: {
        '01': {
          identity: { id: '10101', uptie: 3, level: 45 },
          egos: { ZAYIN: { id: '20101', threadspin: 2 } },
        },
        '02': {
          identity: { id: '10201', uptie: 4, level: 50 },
          egos: { ZAYIN: { id: '20201', threadspin: 1 } },
        },
      },
      deploymentOrder: [0, 1],
      skillEAState: {},
      floorSelections: [
        { themePackId: '1001', difficulty: 1, giftIds: ['9300'] },
        { themePackId: null, difficulty: 1, giftIds: [] },
        { themePackId: null, difficulty: 1, giftIds: [] },
      ],
      sectionNotes: {},
    })
  })

  it('is a no-op on its own output', () => {
    const once = normalizePlannerIds(staleContent(), TABLE)

    expect(normalizePlannerIds(once, TABLE)).toEqual(once)
  })

  it('collapses a renamed id onto the current id already present', () => {
    const content = { selectedGiftIds: ['9247', '9300'] }

    expect(normalizePlannerIds(content, TABLE).selectedGiftIds).toEqual(['9300'])
  })

  it('leaves content unchanged under an empty table', () => {
    expect(normalizePlannerIds(staleContent(), EMPTY_ID_MIGRATION_TABLE)).toEqual(staleContent())
  })

  it('leaves ids outside the gift encoding for strict validation to reject', () => {
    const content = { selectedGiftIds: ['39247', 'x'] }

    expect(normalizePlannerIds(content, TABLE).selectedGiftIds).toEqual(['39247', 'x'])
  })
})

describe('retired E.G.O slots', () => {
  const RETIRED_EGO = '20299'

  function sinnerWith(egos: Record<string, unknown>): Record<string, unknown> {
    return { equipment: { '01': { identity: { id: '10101', uptie: 3, level: 45 }, egos } } }
  }

  it('leaves a retired E.G.O in the ZAYIN slot for validation to report', () => {
    const content = sinnerWith({ ZAYIN: { id: RETIRED_EGO, threadspin: 2 } })

    expect(normalizePlannerIds(content, TABLE)).toEqual(content)
  })

  it('removes a retired E.G.O from the TETH slot', () => {
    const content = sinnerWith({
      ZAYIN: { id: '20101', threadspin: 2 },
      TETH: { id: RETIRED_EGO, threadspin: 1 },
    })

    expect(normalizePlannerIds(content, TABLE)).toEqual(
      sinnerWith({ ZAYIN: { id: '20101', threadspin: 2 } }),
    )
  })
})

describe('withNormalizedIds', () => {
  it('normalizes a Mirror Dungeon planner content', () => {
    const planner = buildSaveablePlanner()
    const content = planner.content as unknown as Record<string, unknown>
    const stale = { ...planner, content: { ...content, selectedGiftIds: ['19247'] } }

    const normalized = withNormalizedIds(stale as typeof planner, TABLE)

    expect((normalized.content as unknown as Record<string, unknown>).selectedGiftIds).toEqual([
      '19300',
    ])
  })
})
