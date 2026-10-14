import { describe, it, expect, beforeEach, vi } from 'vitest'

import stageList from '@static/data/battleStageList.json'
import enemy1366 from '@static/data/enemy/1366.json'
import enemy1366I18n from '@static/i18n/EN/enemy/1366.json'
import enemy1474 from '@static/data/enemy/1474.json'
import enemy1474I18n from '@static/i18n/EN/enemy/1474.json'
import enemy8016 from '@static/data/enemy/8016.json'
import enemy8016I18n from '@static/i18n/EN/enemy/8016.json'
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
  BattleEntityI18nSchema,
  EnemyDataSchema,
  type EngagementRow,
} from '../../schemas/BattleDetailSchemas'
import { BattleFacetNamesSchema } from '../../schemas/BattleSpecSchemas'
import {
  engagementLabel,
  engagementPlace,
  stageLabelText,
  suffixedLabel,
  type EngagementSources,
} from '../engagementLabels'
import { rowContentKey } from '../battleDetail'
import { groupSelectorRows, selectorEntries } from '../engagementRows'
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

function storyEntries(raw: unknown, rawI18n: unknown): string[] {
  const group = EnemyDataSchema.parse(raw)
  const i18n = BattleEntityI18nSchema.parse(rawI18n)
  const rows = groupSelectorRows(group.engagements).find((each) => each.battleCase === 'story')
  return selectorEntries(
    rows?.rows ?? [],
    (row) => engagementLabel(row, sources, translate),
    (row) => rowContentKey(row, group.units[String(row.unit)]?.phases ?? [], i18n),
  ).map((entry) => suffixedLabel(entry, translate))
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

  it('labels an RPG battle with no floor by its node alone', () => {
    expect(
      stageLabelText({ kind: 'rpg', canto: 10, node: 6, route: 'C-1' }, undefined, translate),
    ).toBe('10-6')
  })

  it('labels an RPG battle on a floor by its node and floor', () => {
    expect(
      stageLabelText(
        { kind: 'rpg', canto: 10, node: 6, route: 'C-2', floor: '7' },
        undefined,
        translate,
      ),
    ).toBe('10-6 · Floor 7')
  })

  it('parses an RPG label with or without a floor and rejects an unknown key', () => {
    const stage = stageList['storybattle-a1c10p2.json#11033']
    const withLabels = (labels: unknown[]) =>
      BattleStageListSchema.safeParse({ 'rpg#1': { ...stage, labels } }).success
    const floorless = { kind: 'rpg', canto: 10, node: 6, route: 'C-1' }
    expect(withLabels([floorless])).toBe(true)
    expect(withLabels([{ ...floorless, floor: '7' }])).toBe(true)
    expect(withLabels([{ ...floorless, extra: 1 }])).toBe(false)
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

  it('keeps three numbered entries for the three units stage 10570 spawns', () => {
    const night = 'The 8th Walpurgis Night · Normal (Lv. 45)'
    const labels = storyEntries(enemy1366, enemy1366I18n)
    expect(labels.filter((label) => label.startsWith(night))).toEqual([
      `${night} (1)`,
      `${night} (2)`,
      `${night} (3)`,
    ])
  })

  it('collapses the two story-dungeon rows of 8016 with the same label and content', () => {
    const labels = storyEntries(enemy8016, enemy8016I18n)
    expect(labels.filter((label) => label === 'Canto II · Snowstorm Fields (Lv. 14)')).toHaveLength(
      1,
    )
  })

  it('collapses stages 11008 and 11024 of 1474, whose content matches', () => {
    const labels = storyEntries(enemy1474, enemy1474I18n)
    expect(labels.filter((label) => label.startsWith('10-4 · Floor B1 (Lv. 35)'))).toEqual([
      '10-4 · Floor B1 (Lv. 35)',
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
