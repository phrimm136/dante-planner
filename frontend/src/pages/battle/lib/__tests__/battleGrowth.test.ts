import { describe, it, expect } from 'vitest'

import assistant400001 from '@static/data/assistant/400001.json'
import assistant400038 from '@static/data/assistant/400038.json'
import { selectSkillLevel } from '@/shared/skill'
import {
  AssistantDataSchema,
  type BattleGrowth,
  type BattlePhase,
} from '../../schemas/BattleDetailSchemas'
import { phaseTier, skillLevelAdder, statusView } from '../battleDetail'
import {
  clampGrowthInput,
  defaultGrowthInputs,
  growthState,
  grownPhase,
  heldSkillLevel,
  passiveSlotIds,
  type GrowthInputs,
} from '../battleGrowth'

const WISDOM = 'ProjectGSJiaXichunWisdom'
const COURAGE = 'ProjectGSJiaXichunCourage'
const BENEVOLENCE = 'ProjectGSJiaXichunBenevolence'

const xichunKit = AssistantDataSchema.parse(assistant400001).units['400005']
if (xichunKit?.phases[0].growth === undefined) throw new Error('no 400005 growth')
const xichun: BattlePhase = xichunKit.phases[0]
const growth: BattleGrowth = xichunKit.phases[0].growth
const baseLevel = xichunKit.level ?? 0

function skillOf(phase: BattlePhase, skillId: string) {
  const entry = phase.skills[skillId]
  if (entry === undefined) throw new Error(`no skill ${skillId}`)
  return entry
}

function inputs(wisdom: number, courage: number, benevolence: number): GrowthInputs {
  return { [WISDOM]: wisdom, [COURAGE]: courage, [BENEVOLENCE]: benevolence }
}

function shownSkillLevel(phase: BattlePhase, skillId: string, level: number, state: GrowthInputs) {
  const shown = selectSkillLevel(skillOf(phase, skillId).levels, phaseTier(phase, undefined)).level
  const adders = growthState(growth, state, baseLevel).skillLevelAdders
  return level + shown.skillLevelCorrection + skillLevelAdder(shown.defType, adders)
}

const FULL = inputs(1000, 1000, 1000)
const NONE = inputs(0, 0, 0)

describe('Jia Xichun growth data', () => {
  it('ships three tracks up to 1000 at base level 50 without skill level adders', () => {
    expect(growth.tracks).toEqual([WISDOM, COURAGE, BENEVOLENCE])
    expect(growth.max).toBe(1000)
    expect(baseLevel).toBe(50)
    expect('skillLevelAdders' in xichun).toBe(false)
  })

  it('defaults every track to its maximum', () => {
    expect(defaultGrowthInputs(growth)).toEqual(FULL)
  })

  it('clamps an input into 0..max', () => {
    expect(clampGrowthInput(-5, growth)).toBe(0)
    expect(clampGrowthInput(1200, growth)).toBe(1000)
    expect(clampGrowthInput(499.7, growth)).toBe(499)
    expect(clampGrowthInput(Number.NaN, growth)).toBe(0)
  })
})

describe('Jia Xichun at 1000/1000/1000', () => {
  const grown = grownPhase(xichun, FULL)
  const state = growthState(growth, FULL, baseLevel)

  it('reaches level 60 with the full stat adds', () => {
    expect(state).toEqual({
      level: 60,
      hpAdd: 300,
      speedAdd: 3,
      skillLevelAdders: { attack: 7, defense: 4 },
      slashResistAdd: -1.5,
    })
  })

  it('shows the grown status of the client-4540 kit', () => {
    const view = statusView(grown, undefined, [], state.level, phaseTier(grown, undefined))
    expect(grown.hp).toEqual({ defaultStat: 366, incrementByLevel: 2.28 })
    expect(view.hp).toBe(502)
    expect(grown.minSpeedList).toEqual([6, 7, 8, 9])
    expect(grown.maxSpeedList).toEqual([10, 11, 12, 13])
    expect(view.defense).toEqual({ kind: 'shown', value: { level: 60, correction: 0 } })
    expect(view.physical).toEqual({ kind: 'shown', value: { slash: 0.5, pierce: 1, blunt: 0.5 } })
  })

  it('shows skill levels 67, 70 and evade 64', () => {
    expect(shownSkillLevel(grown, '40000101', state.level, FULL)).toBe(67)
    expect(shownSkillLevel(grown, '40000103', state.level, FULL)).toBe(70)
    expect(shownSkillLevel(grown, '40000104', state.level, FULL)).toBe(64)
  })

  it('holds every skill level and the acquired skill 40000501', () => {
    expect(heldSkillLevel(skillOf(xichun, '40000101'), growth, FULL)).toBe(5)
    expect(heldSkillLevel(skillOf(xichun, '40000104'), growth, FULL)).toBe(4)
    expect(Object.keys(grown.skills)).toContain('40000501')
  })

  it('holds the last passive of every slot', () => {
    if (!('battlePassiveList' in xichun.passives)) throw new Error('no passive lists')
    expect(
      passiveSlotIds(xichun.passives.battlePassiveList, xichun.passives.unlock, growth, FULL),
    ).toEqual(xichun.passives.battlePassiveList[5])
  })
})

describe('Jia Xichun at 0/0/0', () => {
  const grown = grownPhase(xichun, NONE)
  const state = growthState(growth, NONE, baseLevel)

  it('stays at level 50 with no track adds', () => {
    expect(state).toEqual({
      level: 50,
      hpAdd: 0,
      speedAdd: 0,
      skillLevelAdders: { attack: 0, defense: 0 },
      slashResistAdd: -1.5,
    })
  })

  it('keeps every skill at level 1 and hides 40000501', () => {
    for (const [id, entry] of Object.entries(grown.skills)) {
      expect([id, entry.levels.map((level) => level.level)]).toEqual([id, [1]])
    }
    expect(Object.keys(grown.skills)).not.toContain('40000501')
  })

  it('keeps the base passives and leaves the locked slot empty', () => {
    if (!('battlePassiveList' in xichun.passives)) throw new Error('no passive lists')
    const slots = passiveSlotIds(
      xichun.passives.battlePassiveList,
      xichun.passives.unlock,
      growth,
      NONE,
    )
    expect(slots.slice(0, 5)).toEqual(['40000101', '40000102', '40000103', undefined, '940000501'])
    expect(slots.slice(5)).toEqual([
      '940000502',
      '940000503',
      '940000504',
      '940000505',
      '940000506',
      '940000507',
      '940000508',
      '940000509',
      '940000510',
      '940000511',
    ])
  })
})

describe('Jia Xichun acquire 40000501', () => {
  it.each([
    ['wisdom 500 and courage 500', inputs(500, 500, 0), true],
    ['wisdom 800 alone', inputs(800, 0, 0), true],
    ['wisdom 499 and courage 1000', inputs(499, 1000, 0), false],
  ])('%s → shown %s', (_, state, shown) => {
    expect(Object.keys(grownPhase(xichun, state).skills).includes('40000501')).toBe(shown)
  })
})

describe('a phase without growth', () => {
  it('keeps the static kit', () => {
    const phase = AssistantDataSchema.parse(assistant400038).units['400038']?.phases[0]
    if (phase === undefined) throw new Error('no 400038 phase')
    expect(phase.growth).toBeUndefined()
    expect(grownPhase(phase, FULL)).toBe(phase)
  })
})
