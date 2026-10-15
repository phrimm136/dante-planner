import { describe, it, expect } from 'vitest'

import enemy1003 from '@static/data/enemy/1003.json'
import enemy1110 from '@static/data/enemy/1110.json'
import enemy1347 from '@static/data/enemy/1347.json'
import enemy8041 from '@static/data/enemy/8041.json'
import enemy8044 from '@static/data/enemy/8044.json'
import enemy8063 from '@static/data/enemy/8063.json'
import enemy1228 from '@static/data/enemy/1228.json'
import enemy8390 from '@static/data/enemy/8390.json'
import enemy1308 from '@static/data/enemy/1308.json'
import enemy1217 from '@static/data/enemy/1217.json'
import enemy1485 from '@static/data/enemy/1485.json'
import assistant400001 from '@static/data/assistant/400001.json'
import assistant400001KR from '@static/i18n/KR/assistant/400001.json'
import assistant400038 from '@static/data/assistant/400038.json'
import assistant400038I18n from '@static/i18n/EN/assistant/400038.json'
import sinnerEvent10101 from '@static/data/sinnerEvent/10101.json'
import sinnerEvent10101I18n from '@static/i18n/EN/sinnerEvent/10101.json'
import {
  AssistantDataSchema,
  BattleEntityI18nSchema,
  EnemyDataSchema,
  SinnerEventDataSchema,
  type BattleKit,
  type BattlePhase,
  type EngagementRow,
} from '../../schemas/BattleDetailSchemas'
import {
  ALL_PARTS,
  acquireConditionText,
  headerMapName,
  isIdentityShaped,
  isShownPassive,
  kitOf,
  phaseTexts,
  rowParts,
  rowUnitId,
  unitName,
  selectedPart,
  skillLevelAdder,
  skillLevelName,
  stageEffectEntries,
  stageEffectGrants,
  statAtLevel,
  statusView,
  visiblePassives,
  visibleSkills,
  withShownPassives,
} from '../battleDetail'

const skillIdsOf = (skills: { id: string }[]) => skills.map((skill) => skill.id)

function firstPhase(group: { units: Record<string, BattleKit> }, unitId: string): BattlePhase {
  const kit = group.units[unitId]
  if (kit === undefined) throw new Error(`no unit ${unitId}`)
  return kit.phases[0]
}

function rowOn(rows: readonly EngagementRow[], stageId: string): EngagementRow {
  const row = rows.find((each) => each.stage.endsWith(`#${stageId}`))
  if (row === undefined) throw new Error(`no row on ${stageId}`)
  return row
}

describe('hp at a level', () => {
  const phase = firstPhase(EnemyDataSchema.parse(enemy8063), '1280')

  it('floors defaultStat + incrementByLevel x level for the unit and each part', () => {
    expect(statAtLevel(phase.hp, 50)).toBe(695)
    expect(phase.parts?.map((part) => (part.hp ? statAtLevel(part.hp, 50) : undefined))).toEqual([
      434, 124,
    ])
  })
})

describe('part selection', () => {
  const phase = firstPhase(EnemyDataSchema.parse(enemy8063), '1280')
  const parts = rowParts(phase, undefined)

  it('offers HEAD and BODY for 1280 and filters HEAD to its linked skills and passive', () => {
    expect(parts.map((part) => part.partType)).toEqual(['HEAD', 'BODY'])

    const head = selectedPart(parts, 813401)
    expect(skillIdsOf(visibleSkills(phase, parts, head, undefined))).toEqual([
      '806301',
      '806302',
      '806303',
      '806305',
      '806307',
    ])
    expect(visiblePassives(phase, parts, head)).toEqual([['806301', 0]])
  })

  it('shows every skill in ascending id order under All', () => {
    expect(
      skillIdsOf(visibleSkills(phase, parts, selectedPart(parts, ALL_PARTS), undefined)),
    ).toEqual(['806301', '806302', '806303', '806304', '806305', '806306', '806307'])
  })

  it('offers the one part of a single-part phase', () => {
    const single = firstPhase(EnemyDataSchema.parse(enemy1110), '1110')
    const parts = rowParts(single, undefined)
    expect(parts.map((part) => part.id)).toEqual([111001])
    expect(selectedPart(parts, 111001)?.partType).toBe('BODY')
  })

  it('lists every head-linked skill under each of the five HEAD parts of phase 8278', () => {
    const kit = EnemyDataSchema.parse(enemy1228).units['8277']
    const heavy = kit?.phases.find((each) => each.id === 8278)
    if (heavy === undefined) throw new Error('phase 8278 missing')
    const heads = rowParts(heavy, undefined)
    expect(heads.map((part) => part.partType)).toEqual(['HEAD', 'HEAD', 'HEAD', 'HEAD', 'HEAD'])
    for (const head of heads) {
      expect(skillIdsOf(visibleSkills(heavy, heads, head, undefined))).toEqual([
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
    const unlinked = firstPhase(EnemyDataSchema.parse(enemy1308), '9620')
    expect(
      skillIdsOf(visibleSkills(unlinked, rowParts(unlinked, undefined), undefined, undefined)),
    ).toContain('962006')
    for (const part of rowParts(unlinked, undefined)) {
      expect(
        skillIdsOf(visibleSkills(unlinked, rowParts(unlinked, undefined), part, undefined)),
      ).not.toContain('962006')
    }
  })

  it('shows a generated part only on the rows that list it', () => {
    const group = EnemyDataSchema.parse(enemy8041)
    const bull = firstPhase(group, '8041')
    const partIds = (row: EngagementRow) => rowParts(bull, row).map((part) => part.id)
    expect(partIds(rowOn(group.engagements, '10406'))).toEqual([804101, 804102])
    expect(partIds(rowOn(group.engagements, '10403'))).toEqual([804101])
  })

  it('lists the generated head part skills only on the rows that carry the head', () => {
    const group = EnemyDataSchema.parse(enemy8041)
    const bull = firstPhase(group, '8041')
    const skillsOn = (stageId: string, selection: 'all' | number) => {
      const row = rowOn(group.engagements, stageId)
      const parts = rowParts(bull, row)
      return skillIdsOf(visibleSkills(bull, parts, selectedPart(parts, selection), row))
    }
    expect(skillsOn('10403', ALL_PARTS)).toEqual(['804101', '804102', '804103', '804104'])
    expect(skillsOn('10406', ALL_PARTS)).toEqual([
      '804101',
      '804102',
      '804103',
      '804104',
      '804105',
      '804106',
    ])
    expect(skillsOn('10406', 804102)).toEqual(['804105', '804106'])
  })
})

describe('trigger-gated skills', () => {
  const group = AssistantDataSchema.parse(assistant400001)
  const kit = group.units['400005']
  if (kit === undefined) throw new Error('no unit 400005')
  const phase: BattlePhase = { ...kit.phases[0], gatedSkillIds: [40000502] }
  const unlocking: EngagementRow = {
    ...rowOn(group.engagements, '911911'),
    unlockedSkillIds: [40000502],
  }
  const other = rowOn(group.engagements, '911909')
  const skillsOn = (row: EngagementRow | undefined) =>
    skillIdsOf(visibleSkills(phase, [], undefined, row))

  it('hides a gated skill on a row that does not unlock it', () => {
    expect(skillsOn(other)).not.toContain('40000502')
  })

  it('hides a gated skill when no row is selected', () => {
    expect(skillsOn(undefined)).not.toContain('40000502')
  })

  it('shows a gated skill on the row that unlocks it', () => {
    expect(skillsOn(unlocking)).toContain('40000502')
  })

  it('shows an ungated swapped skill on every row', () => {
    for (const row of [unlocking, other, undefined]) expect(skillsOn(row)).toContain('40000501')
  })

  it('changes nothing for an unlocking row on a phase without gated skills', () => {
    const { gatedSkillIds: _gatedSkillIds, ...ungated } = kit.phases[0]
    expect(ungated).not.toHaveProperty('gatedSkillIds')
    expect(skillIdsOf(visibleSkills(ungated, [], undefined, unlocking))).toEqual(
      skillIdsOf(visibleSkills(ungated, [], undefined, other)),
    )
  })

  describe('combined with part selection', () => {
    const base = firstPhase(EnemyDataSchema.parse(enemy8063), '1280')
    const gated: BattlePhase = { ...base, gatedSkillIds: [806305] }
    const parts = rowParts(gated, undefined)
    const [row] = EnemyDataSchema.parse(enemy8063).engagements
    if (row === undefined) throw new Error('no row on 8063')
    const unlocked: EngagementRow = { ...row, unlockedSkillIds: [806305] }
    const skillsOn = (selection: 'all' | number, each: EngagementRow) =>
      skillIdsOf(visibleSkills(gated, parts, selectedPart(parts, selection), each))

    it('drops the gated skill from All and from its part on a row without the unlock', () => {
      expect(skillsOn(ALL_PARTS, row)).toEqual([
        '806301',
        '806302',
        '806303',
        '806304',
        '806306',
        '806307',
      ])
      expect(skillsOn(813401, row)).toEqual(['806301', '806302', '806303', '806307'])
    })

    it('keeps the gated skill under All and its part on the unlocking row', () => {
      expect(skillsOn(ALL_PARTS, unlocked)).toContain('806305')
      expect(skillsOn(813401, unlocked)).toEqual(['806301', '806302', '806303', '806305', '806307'])
    })
  })
})

describe('displayed unit', () => {
  it('takes the row unit, else the group id, else the first unit', () => {
    const row = rowOn(EnemyDataSchema.parse(enemy8063).engagements, '2069001')
    expect(rowUnitId(['1280', '8063'], '8063', row)).toBe('1280')
    expect(rowUnitId(['1280', '8063'], '8063', undefined)).toBe('8063')
    expect(rowUnitId(['1280'], '8063', undefined)).toBe('1280')
  })
})

describe('displayed kit', () => {
  const group = EnemyDataSchema.parse(enemy8063)

  it('takes the kit of the row unit', () => {
    expect(kitOf(group.units, '1280')).toBe(group.units['1280'])
  })

  it('throws when the row unit is not a unit of the group', () => {
    expect(() => kitOf(group.units, '8070')).toThrow('battle group has no unit 8070')
  })
})

describe('unit name', () => {
  it('falls back to the group id when the unit has no name', () => {
    const i18n = { units: { '8063': { name: 'King Trash Crab' }, '1280': {} } }
    expect(unitName(i18n, '8063', '8063')).toBe('King Trash Crab')
    expect(unitName(i18n, '1280', '8063')).toBe('8063')
  })
})

describe('shown passives', () => {
  it('drops the passives assistant 400038 marks hidden, 9999994 among them', () => {
    const group = AssistantDataSchema.parse(assistant400038)
    const phase = firstPhase(group, '400038')
    const texts = phaseTexts(
      BattleEntityI18nSchema.parse(assistant400038I18n),
      '400038',
      '400038',
    )?.passives
    expect(
      visiblePassives(phase, rowParts(phase, undefined), undefined)
        .map(([id]) => id)
        .filter((id) => isShownPassive(texts, id)),
    ).toEqual(['40003801', '40003802', '40003803', '40003804'])
  })

  it('drops a passive without any text', () => {
    expect(isShownPassive({}, '9999994')).toBe(false)
    expect(isShownPassive(undefined, '9999994')).toBe(false)
  })

  it('keeps the uptie slots of an identity-shaped list while dropping hidden passives', () => {
    const phase = firstPhase(SinnerEventDataSchema.parse(sinnerEvent10101), '10101')
    const texts = phaseTexts(
      BattleEntityI18nSchema.parse(sinnerEvent10101I18n),
      '10101',
      '10101',
    )?.passives
    if (!isIdentityShaped(phase.passives)) throw new Error('10101 is not identity-shaped')
    const hidden = { ...texts, '1010121': { hidden: true } }
    const shown = withShownPassives(phase.passives, hidden)
    expect(shown.battlePassiveList).toEqual([['1010101'], [], [], []])
    expect(shown.supportPassiveList).toEqual([[], [], [], []])
  })
})

describe('stage effects of the Warden row', () => {
  const row = rowOn(EnemyDataSchema.parse(enemy1485).engagements, '11036')
  const keywords = stageEffectGrants(row).map((grant) => grant.keywordId)

  it('shows every grant the static data does not make the unit own', () => {
    expect(keywords).toContain('MeursaultBuffWarderThirdPhase')
    expect(keywords).toContain('ChargeNoir')
  })

  it('leaves out the grants the static data makes the unit own', () => {
    expect(keywords.filter((keyword) => keyword.startsWith('Golden'))).toEqual([])
  })

  it('keeps the first grant of a keyword granted more than once', () => {
    const first = row.facts.buffGrants.find(
      (grant) => grant.keywordId === 'ChargeNoir' && !grant.derivable,
    )
    expect(stageEffectGrants(row).filter((grant) => grant.keywordId === 'ChargeNoir')).toEqual([
      first,
    ])
  })
})

describe('stage effect grants', () => {
  const row = rowOn(EnemyDataSchema.parse(enemy8390).engagements, '10710')
  const keywords = stageEffectGrants(row).map((grant) => grant.keywordId)

  it('keeps only the keywords no spawned unit explains, once each', () => {
    expect(keywords).not.toContain('FragmentOfHope')
    expect(keywords).toContain('UnfinishedDream')
    expect(new Set(keywords).size).toBe(keywords.length)
  })

  it('shows a stage script grant whose keyword the unit text also names', () => {
    expect(keywords).toContain('FerrisWheel')
  })

  it('drops a grant a spawned unit already explains', () => {
    const grants = row.facts.buffGrants.filter((grant) => grant.keywordId === 'FragmentOfHope')
    expect(grants.every((grant) => grant.derivable)).toBe(true)
  })
})

describe('status view', () => {
  const blank = { kind: 'blank' }
  const whole1003 = firstPhase(EnemyDataSchema.parse(enemy1003), '1003')
  const parts1003 = rowParts(whole1003, undefined)
  const shadow8678 = firstPhase(EnemyDataSchema.parse(enemy1217), '8678')
  const shadowShown = {
    hp: 188,
    speed: { kind: 'shown', value: { min: 2, max: 5 } },
    defense: { kind: 'shown', value: { level: 43, correction: 3 } },
    physical: { kind: 'shown', value: { slash: 2, pierce: 0.5, blunt: 1 } },
    sins: {
      kind: 'shown',
      value: {
        CRIMSON: 1,
        SCARLET: 1,
        AMBER: 1,
        SHAMROCK: 1,
        AZURE: 2,
        INDIGO: 0.75,
        VIOLET: 2,
        WHITE: 2,
        BLACK: 2,
      },
    },
    breakSection: [70, 30],
  }

  it('blanks everything but HP for the whole of two-part abnormality 1003', () => {
    expect(parts1003.map((part) => part.id)).toEqual([100301, 100302])
    expect(statusView(whole1003, undefined, parts1003, 40, 1)).toEqual({
      hp: 657,
      speed: blank,
      defense: blank,
      physical: blank,
      sins: blank,
      breakSection: undefined,
    })
  })

  it('blanks the whole of one-part abnormality 1347 and shows its part when selected', () => {
    const phase = firstPhase(EnemyDataSchema.parse(enemy1347), '1347')
    const parts = rowParts(phase, undefined)
    expect(parts).toHaveLength(1)
    const whole = statusView(phase, undefined, parts, 40, 1)
    expect([whole.speed, whole.defense, whole.physical, whole.sins]).toEqual([
      blank,
      blank,
      blank,
      blank,
    ])
    const body = statusView(phase, parts[0], parts, 40, 1)
    expect(body.speed).toEqual({ kind: 'shown', value: { min: 1, max: 2 } })
    expect(body.physical).toEqual({
      kind: 'shown',
      value: { slash: 1.2, pierce: 0.9, blunt: 0.9 },
    })
    expect(body.breakSection).toEqual([60])
  })

  it('shows the values of part 100302 when it is selected', () => {
    const pearl = selectedPart(parts1003, 100302)
    const view = statusView(whole1003, pearl, parts1003, 40, 1)
    expect(view.hp).toBe(520)
    expect(view.defense).toEqual({ kind: 'shown', value: { level: 35, correction: -5 } })
    expect(view.sins).toEqual(
      expect.objectContaining({ value: expect.objectContaining({ CRIMSON: 1.2, INDIGO: 1.2 }) }),
    )
    expect(view.breakSection).toEqual([50])
  })

  it('keeps every value of shadow abnormality 8678 under All', () => {
    expect(shadow8678.unitKeywordList).toContain('SHADOW_ENEMY')
    expect(statusView(shadow8678, undefined, rowParts(shadow8678, undefined), 40, 1)).toEqual(
      shadowShown,
    )
  })

  it('blanks 8678 once its unit keywords lose SHADOW_ENEMY', () => {
    const unshadowed = { ...shadow8678, unitKeywordList: [] }
    expect(statusView(unshadowed, undefined, rowParts(unshadowed, undefined), 40, 1).speed).toEqual(
      blank,
    )
  })

  it('reads SHADOW_ENEMY from the unit keywords, not the battle keywords', () => {
    const moved = { ...shadow8678, unitKeywordList: [], battleKeywordList: ['SHADOW_ENEMY'] }
    expect(statusView(moved, undefined, rowParts(moved, undefined), 40, 1).physical).toEqual(blank)
  })

  it('keeps the phase values of a non-abnormality with parts', () => {
    const enemy: BattlePhase = { ...shadow8678, ownerType: 'ENEMY', unitKeywordList: [] }
    expect(statusView(enemy, undefined, rowParts(enemy, undefined), 40, 1)).toEqual(shadowShown)
  })

  it('keeps the phase values of an abnormality with no parts', () => {
    const lone: BattlePhase = { ...shadow8678, unitKeywordList: [], parts: [] }
    expect(statusView(lone, undefined, [], 40, 1)).toEqual(shadowShown)
  })
})

describe('grown assist skills', () => {
  const xichun = firstPhase(AssistantDataSchema.parse(assistant400001), '400005')
  const xichunTexts = phaseTexts(
    BattleEntityI18nSchema.parse(assistant400001KR),
    '400005',
    '400005',
  )
  const KR_JOINERS = { and: ', ', or: ' 또는 ' }

  function acquireOf(skillId: string) {
    const acquire = xichun.skills[skillId]?.acquire
    if (acquire === undefined) throw new Error(`skill ${skillId} has no acquire`)
    return acquire
  }

  const ADDERS = { attack: 7, defense: 4 }

  it('adds the attack adder to attack and counter skills', () => {
    expect(skillLevelAdder('ATTACK', ADDERS)).toBe(7)
    expect(skillLevelAdder('COUNTER', ADDERS)).toBe(7)
  })

  it('adds the defense adder to guard and evade skills', () => {
    expect(skillLevelAdder('GUARD', ADDERS)).toBe(4)
    expect(skillLevelAdder('EVADE', ADDERS)).toBe(4)
  })

  it('reads the raw defense level of 400005, without the defense adder', () => {
    expect(statusView(xichun, undefined, [], 60, 5).defense).toEqual({
      kind: 'shown',
      value: { level: 60, correction: 0 },
    })
  })

  it('adds nothing without adders', () => {
    expect(skillLevelAdder('ATTACK', undefined)).toBe(0)
    expect(skillLevelAdder('GUARD', undefined)).toBe(0)
  })

  it('names a level of 40000101 and 40000104 by its own text', () => {
    const strike = xichunTexts?.skills?.['40000101']
    const evade = xichunTexts?.skills?.['40000104']
    if (strike === undefined || evade === undefined) throw new Error('no 400005 skill texts')
    expect(skillLevelName(strike.descs, 2, strike.name)).toBe('적격 2형 - 파(破)')
    expect(skillLevelName(strike.descs, 5, strike.name)).toBe('적격 5형 - 진(進)')
    expect(skillLevelName(evade.descs, 4, evade.name)).toBe('수회유환')
  })

  it('falls back to the skill name past the descs or on a desc without a name', () => {
    expect(skillLevelName([{ name: 'Lv1' }], 2, 'Skill')).toBe('Skill')
    expect(skillLevelName([{ desc: 'text' }], 1, 'Skill')).toBe('Skill')
  })

  it('joins the acquire sets of 40000501 by or and their conditions by and', () => {
    expect(
      acquireConditionText(acquireOf('40000501'), xichunTexts?.conditionNames, KR_JOINERS),
    ).toBe('지(智) 800 또는 지(智) 500, 용(勇) 500')
  })

  it('writes one AND set without or, and an unnamed condition by its key', () => {
    const [, both] = acquireOf('40000501')
    if (both === undefined) throw new Error('no second acquire set')
    expect(acquireConditionText([both], { ProjectGSJiaXichunWisdom: '지(智)' }, KR_JOINERS)).toBe(
      '지(智) 500, ProjectGSJiaXichunCourage 500',
    )
  })

  function unlockOf(passiveId: string) {
    const { passives } = xichun
    const unlocks = isIdentityShaped(passives) ? (passives.unlock?.['1'] ?? {}) : {}
    const unlock = Object.entries(unlocks).find(([id]) => id === passiveId)?.[1]
    if (unlock === undefined) throw new Error(`passive ${passiveId} has no unlock`)
    return unlock
  }

  it('names the lesson-grade unlock of 940000501 by the lesson and its grade word', () => {
    expect(
      acquireConditionText(
        [unlockOf('940000501')],
        xichunTexts?.conditionNames,
        KR_JOINERS,
        xichunTexts?.conditionGrades,
      ),
    ).toBe('거울을 통해 본 자아와 세계의 형이상학적 탐구 고급')
  })

  it('writes the lesson grade as its number without grade words', () => {
    expect(
      acquireConditionText([unlockOf('940000501')], xichunTexts?.conditionNames, KR_JOINERS),
    ).toBe('거울을 통해 본 자아와 세계의 형이상학적 탐구 3')
  })

  it('keeps track conditions as numbers beside grade words', () => {
    expect(
      acquireConditionText(
        acquireOf('40000501'),
        xichunTexts?.conditionNames,
        KR_JOINERS,
        xichunTexts?.conditionGrades,
      ),
    ).toBe('지(智) 800 또는 지(智) 500, 용(勇) 500')
  })
})

describe('header map', () => {
  const camellia = EnemyDataSchema.parse(enemy8044)
  const row = camellia.engagements.find((each) => each.stage === 'storybattle1-4.json#10448')
  const [first, second] = camellia.units['8044']?.phases ?? []

  it('shows the second map state on phase 80442 and the row map on 8044', () => {
    if (row === undefined || first === undefined || second === undefined) {
      throw new Error('no Camellia row or phases')
    }
    expect([first.id, second.id]).toEqual([8044, 80442])
    expect(headerMapName(row, second)).toBe('Cp4_SheddingTears__second')
    expect(headerMapName(row, first)).toBe('Cp4_SheddingTears')
  })

  it('has no map without a row', () => {
    if (first === undefined) throw new Error('no Camellia phase')
    expect(headerMapName(undefined, first)).toBeUndefined()
  })
})

describe('stage effect entries', () => {
  const grant = (keywordId: string, target: 'enemy' | 'ally' | 'unknown') => ({
    buffId: 1,
    keywordId,
    target,
    stack: 1,
    derivable: false,
    source: { kind: 'stageScript' as const, class: 'StageScript_Test' },
  })

  it('lists the form start buffs before the row grants, unsided', () => {
    expect(
      stageEffectEntries(
        [{ keywordId: 'Charge', stack: 3 }, { keywordId: 'BuffetShoes' }],
        [grant('MeursaultBuffet', 'unknown'), grant('Laceration', 'enemy')],
      ),
    ).toEqual([
      { keywordId: 'Charge', stack: 3, side: undefined },
      { keywordId: 'BuffetShoes', stack: undefined, side: undefined },
      { keywordId: 'MeursaultBuffet', stack: 1, side: undefined },
      { keywordId: 'Laceration', stack: 1, side: 'enemy' },
    ])
  })

  it('keeps one entry per keyword, the form start buff', () => {
    expect(
      stageEffectEntries([{ keywordId: 'Charge', stack: 4 }], [grant('Charge', 'ally')]),
    ).toEqual([{ keywordId: 'Charge', stack: 4, side: undefined }])
  })

  it('lists the row grants alone without start buffs', () => {
    expect(stageEffectEntries([], [grant('Laceration', 'ally')])).toEqual([
      { keywordId: 'Laceration', stack: 1, side: 'ally' },
    ])
  })
})
