import { describe, it, expect } from 'vitest'

import enemy1080 from '@static/data/enemy/1080.json'
import enemy1110 from '@static/data/enemy/1110.json'
import enemy1280 from '@static/data/enemy/1280.json'
import enemy8277 from '@static/data/enemy/8277.json'
import enemy9620 from '@static/data/enemy/9620.json'
import assistant400001 from '@static/data/assistant/400001.json'
import assistant400038 from '@static/data/assistant/400038.json'
import { AssistantDataSchema, EnemyDataSchema } from '../../schemas/BattleDetailSchemas'
import {
  ALL_PARTS,
  defaultEngagementLevel,
  engagementLevels,
  selectedPart,
  selectorParts,
  statAtLevel,
  visiblePassives,
  visibleSkills,
} from '../battleDetail'
import type { BattleCase } from '../battleVocabulary'

const NO_CASES: ReadonlySet<BattleCase> = new Set()

const skillIdsOf = (skills: { id: string }[]) => skills.map((skill) => skill.id)

describe('engagement levels', () => {
  const record = EnemyDataSchema.parse(enemy1080)

  it('lists the distinct engagement levels in ascending order', () => {
    expect(engagementLevels(record.engagedStages, undefined)).toEqual([50, 51, 60])
  })

  it('defaults to the first engagement when no case filter is active', () => {
    expect(defaultEngagementLevel(record.engagedStages, NO_CASES, undefined)).toBe(51)
  })

  it('defaults to the first engagement of the case the user arrived from', () => {
    expect(
      defaultEngagementLevel(record.engagedStages, new Set(['mirrorDungeon']), undefined),
    ).toBe(50)
  })

  it('offers one level for an entity engaged at a single level', () => {
    const assistant = AssistantDataSchema.parse(assistant400001)
    expect(engagementLevels(assistant.engagedStages, assistant.level)).toEqual([50])
  })

  it('offers no level when every engagement and the record leave it unset', () => {
    const assistant = AssistantDataSchema.parse(assistant400038)
    expect(engagementLevels(assistant.engagedStages, assistant.level)).toEqual([])
    expect(defaultEngagementLevel(assistant.engagedStages, NO_CASES, assistant.level)).toBe(
      undefined,
    )
  })
})

describe('hp at a level', () => {
  const [phase] = EnemyDataSchema.parse(enemy1280).phases

  it('floors defaultStat + incrementByLevel x level for the unit and each part', () => {
    expect(statAtLevel(phase.hp, 50)).toBe(695)
    expect(phase.parts?.map((part) => (part.hp ? statAtLevel(part.hp, 50) : undefined))).toEqual([
      434, 124,
    ])
  })
})

describe('part selection', () => {
  it('offers HEAD and BODY for 1280 and filters HEAD to its linked skills and passive', () => {
    const [phase] = EnemyDataSchema.parse(enemy1280).phases
    expect(selectorParts(phase).map((part) => part.partType)).toEqual(['HEAD', 'BODY'])

    const head = selectedPart(phase, 813401)
    expect(skillIdsOf(visibleSkills(phase, head))).toEqual([
      '806301',
      '806302',
      '806303',
      '806305',
      '806307',
    ])
    expect(visiblePassives(phase, head)).toEqual([['806301', 0]])
  })

  it('shows every skill in ascending id order under All', () => {
    const [phase] = EnemyDataSchema.parse(enemy1280).phases
    expect(skillIdsOf(visibleSkills(phase, selectedPart(phase, ALL_PARTS)))).toEqual([
      '806301',
      '806302',
      '806303',
      '806304',
      '806305',
      '806306',
      '806307',
    ])
  })

  it('offers no part selector for a single-part phase', () => {
    const [phase] = EnemyDataSchema.parse(enemy1110).phases
    expect(selectorParts(phase)).toEqual([])
  })

  it('lists every head-linked skill under each of the five HEAD parts of phase 8278', () => {
    const phase = EnemyDataSchema.parse(enemy8277).phases.find((each) => each.id === 8278)
    if (phase === undefined) throw new Error('phase 8278 missing')
    const heads = selectorParts(phase)
    expect(heads.map((part) => part.partType)).toEqual(['HEAD', 'HEAD', 'HEAD', 'HEAD', 'HEAD'])
    for (const head of heads) {
      expect(skillIdsOf(visibleSkills(phase, head))).toEqual([
        '827801',
        '827802',
        '827803',
        '827804',
        '827805',
        '827806',
        '827807',
        '827808',
      ])
    }
  })

  it('keeps an unlinked skill under All only', () => {
    const [phase] = EnemyDataSchema.parse(enemy9620).phases
    expect(skillIdsOf(visibleSkills(phase, undefined))).toContain('962006')
    for (const part of selectorParts(phase)) {
      expect(skillIdsOf(visibleSkills(phase, part))).not.toContain('962006')
    }
  })
})
