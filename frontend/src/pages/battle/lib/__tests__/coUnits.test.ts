import { describe, it, expect } from 'vitest'

import enemy1137 from '@static/data/enemy/1137.json'
import enemy8193 from '@static/data/enemy/8193.json'
import enemy8556 from '@static/data/enemy/8556.json'
import enemy8564 from '@static/data/enemy/8564.json'
import assistantSpec from '@static/data/assistantSpecList.json'
import enemySpec from '@static/data/enemySpecList.json'
import sinnerEventSpec from '@static/data/sinnerEventSpecList.json'
import assistantNamesKR from '@static/i18n/KR/assistantNameList.json'
import enemyNamesKR from '@static/i18n/KR/enemyNameList.json'
import identityNamesKR from '@static/i18n/KR/identityNameList.json'
import sinnerEventNamesKR from '@static/i18n/KR/sinnerEventNameList.json'
import { AssistantSpecListSchema } from '../../schemas/AssistantSchemas'
import { EnemyDataSchema, type EngagementRow } from '../../schemas/BattleDetailSchemas'
import { BattleNameListSchema } from '../../schemas/BattleSpecSchemas'
import { EnemySpecListSchema } from '../../schemas/EnemySchemas'
import { SinnerEventSpecListSchema } from '../../schemas/SinnerEventSchemas'
import { coUnitChips, type CoUnitCard, type CoUnitCatalog } from '../coUnits'

const catalog: CoUnitCatalog = {
  specs: {
    enemy: EnemySpecListSchema.parse(enemySpec),
    assistant: AssistantSpecListSchema.parse(assistantSpec),
    sinnerEvent: SinnerEventSpecListSchema.parse(sinnerEventSpec),
  },
  names: {
    enemy: BattleNameListSchema.parse(enemyNamesKR),
    assistant: BattleNameListSchema.parse(assistantNamesKR),
    sinnerEvent: BattleNameListSchema.parse(sinnerEventNamesKR),
  },
  identityTitles: identityNamesKR,
}

const card = (id: string): CoUnitCard => ({ family: 'enemy', id })

function rowOf(raw: unknown, unit: number, stageId: string): EngagementRow {
  const row = EnemyDataSchema.parse(raw).engagements.find(
    (each) => each.unit === unit && each.stage.endsWith(`#${stageId}`),
  )
  if (row === undefined) throw new Error(`no ${String(unit)} row on ${stageId}`)
  return row
}

const chipText = (row: EngagementRow, current: CoUnitCard) =>
  coUnitChips(row, catalog, current).map((chip) => [chip.label, chip.relation])

describe('co-appearing units', () => {
  it('marks the roses 장미 표지판 1026 summons on 2067035', () => {
    const chips = chipText(rowOf(enemy8556, 1026, '2067035'), card('8556'))
    expect(chips).toContainEqual(['분노를 머금은 장미', 'summons'])
    expect(chips).toContainEqual(['색욕을 머금은 장미', 'summons'])
  })

  it('marks 장미 표지판 as the summoner on the page of rose 1027', () => {
    const chips = chipText(rowOf(enemy8564, 1027, '2067035'), card('8564'))
    expect(chips).toContainEqual(['장미 표지판', 'summonedBy'])
    expect(chips).toContainEqual(['색욕을 머금은 장미', undefined])
  })

  it('lists 자로 and the two 흑수 on the 가치우 row of 10827 without a relation', () => {
    expect(chipText(rowOf(enemy1137, 1137, '10827'), card('1137'))).toEqual([
      ['자로', undefined],
      ['흑수 - 묘 : 쾌격', undefined],
      ['흑수 - 묘 : 암격', undefined],
    ])
  })

  it('links a chip to the other card on the same stage', () => {
    const [first] = coUnitChips(rowOf(enemy1137, 1137, '10827'), catalog, card('1137'))
    expect(first).toMatchObject({
      family: 'enemy',
      id: '1126',
      stage: 'abbattle-a1c8p2.json#10827',
    })
  })

  it('drops a co-unit whose card is not in the loaded spec lists', () => {
    const row = rowOf(enemy1137, 1137, '10827')
    const missing = { ...row, coUnits: [{ unit: 1, card: { family: 'enemy' as const, id: 1 } }] }
    expect(coUnitChips(missing, catalog, card('1137'))).toEqual([])
  })

  it('shows one chip per card and relation', () => {
    const row = rowOf(enemy1137, 1137, '10827')
    const twice = { ...row, coUnits: [...row.coUnits, ...row.coUnits] }
    expect(coUnitChips(twice, catalog, card('1137'))).toHaveLength(3)
  })

  it('shows nothing on a row without co-units', () => {
    const row = { ...rowOf(enemy1137, 1137, '10827'), coUnits: [] }
    expect(coUnitChips(row, catalog, card('1137'))).toEqual([])
  })

  it('hides a co-unit whose card is the current card', () => {
    const row = rowOf(enemy8193, 8193, '10648')
    expect(row.coUnits).toContainEqual({ unit: 8215, card: { family: 'enemy', id: 8193 } })
    const ids = coUnitChips(row, catalog, card('8193')).map((chip) => chip.id)
    expect(ids).not.toContain('8193')
    expect(ids).toContain('8192')
  })

  it('keeps a co-unit of the same id from another family', () => {
    const row = rowOf(enemy8193, 8193, '10648')
    const ids = coUnitChips(row, catalog, { family: 'assistant', id: '8193' }).map(
      (chip) => chip.id,
    )
    expect(ids).toContain('8193')
  })
})
