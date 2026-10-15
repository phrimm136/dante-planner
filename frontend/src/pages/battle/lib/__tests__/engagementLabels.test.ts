import { describe, it, expect, beforeEach, vi } from 'vitest'

import stageList from '@static/data/battleStageList.json'
import enemy1142 from '@static/data/enemy/1142.json'
import enemy1366 from '@static/data/enemy/1366.json'
import enemy1366I18n from '@static/i18n/EN/enemy/1366.json'
import enemy1474 from '@static/data/enemy/1474.json'
import enemy1474I18n from '@static/i18n/EN/enemy/1474.json'
import enemy8016 from '@static/data/enemy/8016.json'
import enemy8016I18n from '@static/i18n/EN/enemy/8016.json'
import enemy8044 from '@static/data/enemy/8044.json'
import enemy8063 from '@static/data/enemy/8063.json'
import enemy8253 from '@static/data/enemy/8253.json'
import enemy8112 from '@static/data/enemy/8112.json'
import enemy8630 from '@static/data/enemy/8630.json'
import enemy90014 from '@static/data/enemy/90014.json'
import enemy90034 from '@static/data/enemy/90034.json'
import enemy90034I18n from '@static/i18n/EN/enemy/90034.json'
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
  eventPlaceText,
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
    expect(engagementLabel(rowOn(enemy90014, '100101'), sources, translate)).toBe(
      'Line 1 BokGak · First Stop: Contact (Lv. 55)',
    )
  })

  it('labels a mirror dungeon row by its theme pack name', () => {
    expect(engagementLabel(rowOn(enemy8063, '2069001'), sources, translate)).toBe(
      `${enThemePacks['1505'].name} (Lv. 50)`,
    )
  })

  it('labels an RPG battle by route and floor with the same text in each language', async () => {
    const row = rowOn(enemy1474, '11054')
    expect(engagementPlace(row, sources, translate)).toBe('10-B B1F')
    await i18n.changeLanguage('KR')
    await vi.waitFor(() => expect(i18n.language).toBe('KR'))
    expect(engagementPlace(row, sources, translate)).toBe('10-B B1F')
  })

  it('labels an RPG battle on route A by its route letter and floor', () => {
    expect(
      stageLabelText(
        { kind: 'rpg', canto: 10, node: 4, route: 'A', floor: '1' },
        undefined,
        translate,
      ),
    ).toBe('10-A 1F')
  })

  it('labels an RPG battle on a basement floor with the floor suffix', () => {
    expect(
      stageLabelText(
        { kind: 'rpg', canto: 10, node: 5, route: 'B', floor: 'B1' },
        undefined,
        translate,
      ),
    ).toBe('10-B B1F')
  })

  it('labels an RPG battle with no floor by its route letter alone', () => {
    expect(
      stageLabelText({ kind: 'rpg', canto: 10, node: 6, route: 'C-1' }, undefined, translate),
    ).toBe('10-C')
  })

  it('labels an RPG battle on a C sub-route by the letter C and its floor', () => {
    expect(
      stageLabelText(
        { kind: 'rpg', canto: 10, node: 6, route: 'C-2', floor: '7' },
        undefined,
        translate,
      ),
    ).toBe('10-C 7F')
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
      '10-B B1F / 10-B B2F',
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

  it('collapses the story-dungeon rows of 8016 with the same label and content', () => {
    const labels = storyEntries(enemy8016, enemy8016I18n)
    expect(labels.filter((label) => label.startsWith('2-19 (Lv. 14)'))).toEqual(['2-19 (Lv. 14)'])
  })

  it('collapses stages 11008 and 11024 of 1474, whose content matches', () => {
    const labels = storyEntries(enemy1474, enemy1474I18n)
    expect(labels.filter((label) => label.startsWith('10-A B1F (Lv. 35)'))).toEqual([
      '10-A B1F (Lv. 35)',
    ])
  })

  it('omits an event place with no text and keeps the named ones', () => {
    const row = rowOn(enemy8253, '2060018')
    const place = engagementPlace(row, sources, translate)
    expect(place).toBe('A field of white, ye…')
    expect(place).not.toContain('90102001')
  })

  it('falls back to the case label when no place has text', () => {
    const row = { ...rowOn(enemy8253, '2060018'), stage: 'nowhere#1' }
    expect(engagementPlace(row, sources, translate)).toBe('MD')
  })
})

function labelOf(stageKey: string) {
  const label = sources.stages[stageKey]?.labels?.[0]
  if (label === undefined) throw new Error(`no label on ${stageKey}`)
  return label
}

describe('story-dungeon labels', () => {
  it('labels 8016 on 10412 by chapter and dungeon stage with its level', () => {
    expect(engagementLabel(rowOn(enemy8016, '10412'), sources, translate)).toBe('4-54 (Lv. 31)')
  })

  it.each([
    ['8112', enemy8112, '10506', '5-36 (Lv. 39)'],
    ['1142', enemy1142, '10801', '8-20 (Lv. 46)'],
  ] as const)(
    'labels the row of %s on %s by chapter and dungeon node count',
    (_, raw, stageId, label) => {
      expect(engagementLabel(rowOn(raw, stageId), sources, translate)).toBe(label)
    },
  )

  it('keeps one 3-22 entry per level for the dungeon 10301 rows of 90034, whose content matches per level', () => {
    const labels = storyEntries(enemy90034, enemy90034I18n)
    expect(labels.filter((label) => label.startsWith('3-22 '))).toEqual([
      '3-22 (Lv. 23)',
      '3-22 (Lv. 24)',
      '3-22 (Lv. 25)',
      '3-22 (Lv. 26)',
      '3-22 (Lv. 27)',
      '3-22 (Lv. 28)',
    ])
  })

  it('labels the 5.5 dungeon 910701 by intervallo part and dungeon stage', () => {
    expect(stageLabelText(labelOf('dungeonbattle-ycgd.json#910701'), undefined, translate)).toBe(
      '5.5-2-24',
    )
  })

  it('labels the 8.5 EX pilgrimage by the EX part and node', () => {
    expect(
      stageLabelText(labelOf('storybattle-pilgrimage-normal.json#912001'), undefined, translate),
    ).toBe('8.5-EX-1')
  })

  it('labels the hard 8.5 EX pilgrimage with the same node and Hard', () => {
    expect(
      stageLabelText(labelOf('storybattle-pilgrimage-hard.json#912104'), undefined, translate),
    ).toBe('8.5-EX-4 · Hard')
  })

  it('labels a hard 8.5 EX dungeon battle by its dungeon stage and Hard', () => {
    expect(stageLabelText(labelOf('abbattle-a1c9121.json#912104'), undefined, translate)).toBe(
      '8.5-EX-14 · Hard',
    )
  })

  it('rejects a story-dungeon label carrying a dungeon node id', () => {
    const stage = stageList['abbattle1-4.json#10412']
    const label = { ...labelOf('abbattle1-4.json#10412'), dungeonNodeId: '10412' }
    expect(BattleStageListSchema.safeParse({ 'd#1': { ...stage, labels: [label] } }).success).toBe(
      false,
    )
  })
})

describe('mirror event places', () => {
  it('shows the first 20 characters of the 901063 event text on its row', () => {
    expect(engagementPlace(rowOn(enemy8630, '2070512'), sources, translate)).toBe(
      "'This is a warzone.'…",
    )
  })

  it('shows an event text of 20 characters or fewer whole', () => {
    const stage = sources.stages['mirrordungeon-abbattle7-0.json#2070512']
    if (stage === undefined) throw new Error('no stage 2070512')
    const short: EngagementSources = {
      ...sources,
      stages: { ...sources.stages, 'short#1': { ...stage, mirror: { eventIds: ['1'] } } },
      facetNames: { ...sources.facetNames, events: { '1': 'Twenty chars exactly' } },
    }
    const row = { ...rowOn(enemy8630, '2070512'), stage: 'short#1' }
    expect(engagementPlace(row, short, translate)).toBe('Twenty chars exactly')
  })

  it('counts characters by code point', () => {
    expect(eventPlaceText('\u{1D49C}'.repeat(21))).toBe(`${'\u{1D49C}'.repeat(20)}…`)
    expect(eventPlaceText('\u{1D49C}'.repeat(20))).toBe('\u{1D49C}'.repeat(20))
  })
})
