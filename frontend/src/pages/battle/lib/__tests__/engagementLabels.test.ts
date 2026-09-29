import { describe, it, expect, beforeEach, vi } from 'vitest'

import stageList from '@static/data/battleStageList.json'
import enemy1366 from '@static/data/enemy/1366.json'
import enemy1474 from '@static/data/enemy/1474.json'
import enemy8044 from '@static/data/enemy/8044.json'
import enemy8063 from '@static/data/enemy/8063.json'
import enemy8253 from '@static/data/enemy/8253.json'
import enemy9501 from '@static/data/enemy/9501.json'
import enStageTexts from '@static/i18n/EN/battleStageList.json'
import enFacetNames from '@static/i18n/EN/battleFacetNames.json'
import enThemePacks from '@static/i18n/EN/themePack.json'
import i18n from '@/lib/i18n'
import { ThemePackI18nSchema } from '@/shared/themePack'
import {
  BattleStageListSchema,
  BattleStageTextsSchema,
  EnemyDataSchema,
  type EngagementRow,
} from '../../schemas/BattleDetailSchemas'
import { BattleFacetNamesSchema } from '../../schemas/BattleSpecSchemas'
import {
  engagementLabel,
  engagementPlace,
  selectorRowLabel,
  type EngagementSources,
} from '../engagementLabels'
import { groupSelectorRows } from '../engagementRows'
import type { Translate } from '../enemy/enemyFacetOptions'

const translate: Translate = (key, options) => i18n.t(key, { ns: 'database', ...options })

const sources: EngagementSources = {
  stages: BattleStageListSchema.parse(stageList),
  texts: BattleStageTextsSchema.parse(enStageTexts),
  facetNames: BattleFacetNamesSchema.parse(enFacetNames),
  themePacks: ThemePackI18nSchema.parse(enThemePacks),
}

function rowOn(raw: unknown, stageId: string): EngagementRow {
  const row = EnemyDataSchema.parse(raw).engagements.find((each) =>
    each.stage.endsWith(`#${stageId}`),
  )
  if (row === undefined) throw new Error(`no row on ${stageId}`)
  return row
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('engagement labels', () => {
  it('labels a canto stage by chapter and node with its level', () => {
    expect(engagementLabel(rowOn(enemy8044, '10448'), sources, translate)).toBe('4-48 (Lv. 35)')
  })

  it('labels a walpurgis stage by the night and its difficulty', () => {
    expect(engagementLabel(rowOn(enemy1366, '10570'), sources, translate)).toBe(
      'The 8th Walpurgis Night · Normal (Lv. 45)',
    )
  })

  it('labels a rerun railway node by the rerun line and node short name', () => {
    expect(engagementLabel(rowOn(enemy9501, '100101'), sources, translate)).toBe(
      'Line 1 BokGak · First Stop: Contact (Lv. 55)',
    )
  })

  it('labels a mirror dungeon row by its theme pack name', () => {
    expect(engagementLabel(rowOn(enemy8063, '2069001'), sources, translate)).toBe(
      `${enThemePacks['1505'].name} (Lv. 50)`,
    )
  })

  it('labels an RPG battle by node and floor in each language', async () => {
    const row = rowOn(enemy1474, '11054')
    expect(engagementPlace(row, sources, translate)).toBe('10-5 · Floor B1')
    await i18n.changeLanguage('KR')
    await vi.waitFor(() => expect(engagementPlace(row, sources, translate)).toBe('10-5 · B1층'))
  })

  it('joins every label of a row placed on two floors', () => {
    expect(engagementPlace(rowOn(enemy1474, '11033'), sources, translate)).toBe(
      '10-5 · Floor B1 / 10-5 · Floor B2',
    )
  })

  it('labels a row without a level by its place alone', () => {
    const row = { ...rowOn(enemy8044, '10448'), level: null }
    expect(engagementLabel(row, sources, translate)).toBe('4-48')
  })

  it('suffixes the rows of one stage by the unit they spawn', () => {
    const groups = groupSelectorRows(EnemyDataSchema.parse(enemy1366).engagements)
    const story = groups.find((group) => group.battleCase === 'story')
    const labels = (story?.rows ?? [])
      .filter(({ row }) => row.stage.endsWith('#10570') && row.level === 45)
      .map((each) => [each.row.unit, selectorRowLabel(each, sources, translate)])
    expect(labels).toEqual([
      [1366, 'The 8th Walpurgis Night · Normal (Lv. 45) (1)'],
      [1367, 'The 8th Walpurgis Night · Normal (Lv. 45) (2)'],
      [1368, 'The 8th Walpurgis Night · Normal (Lv. 45) (3)'],
    ])
  })

  it('omits an event place with no text and keeps the named ones', () => {
    const row = rowOn(enemy8253, '2060018')
    const place = engagementPlace(row, sources, translate)
    expect(place).toBe(enFacetNames.events['901020'])
    expect(place).not.toContain('90102001')
  })

  it('falls back to the case label when no place has text', () => {
    const row = { ...rowOn(enemy8253, '2060018'), stage: 'nowhere#1' }
    expect(engagementPlace(row, sources, translate)).toBe('MD')
  })
})
