import { describe, it, expect, beforeEach } from 'vitest'

import stageList from '@static/data/battleStageList.json'
import enemy1280 from '@static/data/enemy/1280.json'
import enemy8044 from '@static/data/enemy/8044.json'
import enemy8265 from '@static/data/enemy/8265.json'
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
} from '../../schemas/BattleDetailSchemas'
import { BattleFacetNamesSchema } from '../../schemas/BattleSpecSchemas'
import { groupEngagements, type EngagementSources } from '../engagementLabels'
import type { Translate } from '../enemy/enemyFacetOptions'

const translate: Translate = (key, options) => i18n.t(key, { ns: 'database', ...options })

const sources: EngagementSources = {
  stages: BattleStageListSchema.parse(stageList),
  texts: BattleStageTextsSchema.parse(enStageTexts),
  facetNames: BattleFacetNamesSchema.parse(enFacetNames),
  themePacks: ThemePackI18nSchema.parse(enThemePacks),
}

function labelsOf(raw: unknown, battleCase: string): string[] {
  const record = EnemyDataSchema.parse(raw)
  const group = groupEngagements(record.engagedStages, sources, translate).find(
    (each) => each.battleCase === battleCase,
  )
  return group?.labels ?? []
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('engagement labels', () => {
  it('labels a canto stage by chapter and node with its level', () => {
    expect(labelsOf(enemy8044, 'story')).toContain('4-48 (Lv. 35)')
  })

  it('labels a walpurgis stage by the night and its difficulty', () => {
    expect(labelsOf(enemy8265, 'story')).toContain('The 3rd Walpurgis Night · Hard (Lv. 45)')
  })

  it('labels a rerun railway node by the rerun line and node short name', () => {
    expect(labelsOf(enemy9501, 'refractionRailway')).toContain(
      'Line 1 BokGak · First Stop: Contact (Lv. 55)',
    )
  })

  it('labels a mirror dungeon engagement by its theme pack name', () => {
    expect(labelsOf(enemy1280, 'mirrorDungeon')).toEqual([`${enThemePacks['1505'].name} (Lv. 50)`])
  })
})
