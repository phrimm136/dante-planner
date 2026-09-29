import { describe, it, expect, beforeEach } from 'vitest'

import enemySpec from '@static/data/enemySpecList.json'
import enFacetNames from '@static/i18n/EN/battleFacetNames.json'
import i18n from '@/lib/i18n'
import { EnemySpecListSchema } from '../../schemas/EnemySchemas'
import {
  chapterLabel,
  collectEnemyFacetKeys,
  luxcavationLabel,
  railwayLineLabel,
  type Translate,
} from '../enemy/enemyFacetOptions'

const translate: Translate = (key, options) => i18n.t(key, { ns: 'database', ...options })

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('enemy facet labels', () => {
  it('labels a rerun line with the rerun template around its line name', () => {
    expect(railwayLineLabel(1001, enFacetNames.railwayLines, translate)).toBe('Line 1 BokGak')
  })

  it('labels a regular line by its line name', () => {
    expect(railwayLineLabel(1, enFacetNames.railwayLines, translate)).toBe('Line 1')
  })

  it('formats cantos, intervallos and the tutorial from templates', () => {
    expect(chapterLabel('canto:4', enFacetNames.chapters, translate)).toBe('Canto 4')
    expect(chapterLabel('intervallo:5.5', enFacetNames.chapters, translate)).toBe('Intervallo 5.5')
    expect(chapterLabel('tutorial', enFacetNames.chapters, translate)).toBe('Tutorial')
  })

  it('reads walpurgis and april fools titles from the facet names', () => {
    expect(chapterLabel('walpurgis:3', enFacetNames.chapters, translate)).toBe(
      'The 3rd Walpurgis Night',
    )
    expect(
      chapterLabel('aprilFools:fools_2025_entrance_popup_title', enFacetNames.chapters, translate),
    ).toBe("A Midspring Night's Dream 2")
  })

  it('labels EXP luxcavation from its template and thread dungeons by name', () => {
    expect(luxcavationLabel('exp', enFacetNames.luxcavations, translate)).toBe('EXP Luxcavation')
    expect(luxcavationLabel('thread:1', enFacetNames.luxcavations, translate)).toBe(
      "Ebony Queen's Apple",
    )
  })
})

describe('enemy facet keys', () => {
  const keys = collectEnemyFacetKeys(Object.values(EnemySpecListSchema.parse(enemySpec)))

  it('orders the tutorial, then cantos and intervallos by number, then walpurgis, then april fools', () => {
    expect(keys.chapters).toEqual([
      'tutorial',
      'canto:1',
      'canto:2',
      'canto:3',
      'intervallo:3.5',
      'canto:4',
      'intervallo:4.5',
      'canto:5',
      'intervallo:5.5',
      'canto:6',
      'intervallo:6.5',
      'canto:7',
      'intervallo:7.5',
      'canto:8',
      'intervallo:8.5',
      'canto:9',
      'intervallo:9.5',
      'canto:10',
      'walpurgis:2',
      'walpurgis:3',
      'walpurgis:4',
      'walpurgis:5',
      'walpurgis:6',
      'walpurgis:8',
      'aprilFools:fools_2025_entrance_popup_title',
      'aprilFools:fools_2026_entrance_popup_title',
    ])
  })

  it('orders the lines numerically with the reruns last', () => {
    expect(keys.railwayLines).toEqual([1, 2, 3, 4, 5, 6, 1001, 1002])
  })

  it('orders EXP luxcavation before the thread dungeons', () => {
    expect(keys.luxcavations).toEqual([
      'exp',
      'thread:1',
      'thread:2',
      'thread:3',
      'thread:4',
      'thread:5',
      'thread:6',
      'thread:7',
    ])
  })
})
