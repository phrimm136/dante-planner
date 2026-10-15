import { describe, expect, it } from 'vitest'

import assistant400001 from '@static/data/assistant/400001.json'
import assistant400038 from '@static/data/assistant/400038.json'
import assistant400041 from '@static/data/assistant/400041.json'
import enemy1099 from '@static/data/enemy/1099.json'
import enemy8044 from '@static/data/enemy/8044.json'
import enemy8063 from '@static/data/enemy/8063.json'
import enemy8556 from '@static/data/enemy/8556.json'
import stageList from '@static/data/battleStageList.json'
import enemy1519 from '@static/data/enemy/1519.json'
import enemy91059 from '@static/data/enemy/91059.json'
import sinnerEvent10101 from '@static/data/sinnerEvent/10101.json'
import {
  AssistantDataSchema,
  BattleStageListSchema,
  EnemyDataSchema,
  SinnerEventDataSchema,
} from '../BattleDetailSchemas'

describe('battle unit owner type', () => {
  it('reads ABNORMALITY on the phase and part of enemy 1519', () => {
    const [phase] = EnemyDataSchema.parse(enemy1519).units['1519']?.phases ?? []
    expect(phase?.ownerType).toBe('ABNORMALITY')
    expect(phase?.parts?.map((part) => part.ownerType)).toEqual(['ABNORMALITY'])
  })

  it('reads ENEMY on the phase of enemy 91059', () => {
    const [phase] = EnemyDataSchema.parse(enemy91059).units['91059']?.phases ?? []
    expect(phase?.ownerType).toBe('ENEMY')
  })

  it('parses assistant 400038 and event sinner 10101 without an owner type', () => {
    const [assistant] = AssistantDataSchema.parse(assistant400038).units['400038']?.phases ?? []
    const [sinner] = SinnerEventDataSchema.parse(sinnerEvent10101).units['10101']?.phases ?? []
    expect(assistant?.ownerType).toBeUndefined()
    expect(sinner?.ownerType).toBeUndefined()
  })

  it('rejects an owner type outside the client enum', () => {
    const [phase] = enemy91059.units['91059'].phases
    const raw = {
      ...enemy91059,
      units: {
        '91059': { ...enemy91059.units['91059'], phases: [{ ...phase, ownerType: 'SINNER' }] },
      },
    }
    expect(EnemyDataSchema.safeParse(raw).success).toBe(false)
  })
})

describe('battle skill linked part types', () => {
  it('reads BODY on every skill of enemy 1519', () => {
    const [phase] = EnemyDataSchema.parse(enemy1519).units['1519']?.phases ?? []
    const linked = Object.fromEntries(
      Object.entries(phase?.skills ?? {}).map(([id, skill]) => [id, skill.linkedPartTypes]),
    )
    expect(linked).toEqual({
      '151901': ['BODY'],
      '151902': ['BODY'],
      '151903': ['BODY'],
      '151904': ['BODY'],
      '151905': ['BODY'],
    })
  })

  it('rejects a linked part type outside AB_PART_TYPE', () => {
    const [phase] = enemy1519.units['1519'].phases
    if (phase === undefined) throw new Error('no phase')
    const skills = {
      ...phase.skills,
      '151901': { ...phase.skills['151901'], linkedPartTypes: ['WING'] },
    }
    const raw = {
      ...enemy1519,
      units: { '1519': { ...enemy1519.units['1519'], phases: [{ ...phase, skills }] } },
    }
    expect(EnemyDataSchema.safeParse(raw).success).toBe(false)
  })

  it('parses enemy 91059 skills without linked part types', () => {
    const [phase] = EnemyDataSchema.parse(enemy91059).units['91059']?.phases ?? []
    const skills = Object.values(phase?.skills ?? {})
    expect(skills.length).toBeGreaterThan(0)
    expect(skills.every((skill) => skill.linkedPartTypes === undefined)).toBe(true)
  })
})

describe('client-dd6f battle fields', () => {
  it('reads the growth rows of Jia Xichun 400005, the slash resist rows by float value', () => {
    const [phase] = AssistantDataSchema.parse(assistant400001).units['400005']?.phases ?? []
    const rows = phase?.growth?.rows ?? []
    expect(phase?.growth?.max).toBe(1000)
    expect(rows.filter((row) => row.category === 'ATK_SLASH_RESIST_UPGRADE')).toHaveLength(6)
    expect(
      rows.every((row) => 'floatValue' in row === (row.category === 'ATK_SLASH_RESIST_UPGRADE')),
    ).toBe(true)
  })

  it('reads the skill level and passive unlocks of 400005', () => {
    const [phase] = AssistantDataSchema.parse(assistant400001).units['400005']?.phases ?? []
    expect(phase?.skills['40000101']?.levels[1]?.unlock).toEqual({
      condition: [{ condition: 'ProjectGSJiaXichunCourage', value: 200 }],
    })
    const passives = phase?.passives
    const unlocks: Record<string, Record<string, unknown>> | undefined =
      passives !== undefined && 'unlock' in passives ? passives.unlock : undefined
    expect(unlocks?.['2']?.['940000516']).toEqual({
      condition: [{ condition: 'ProjectGSJiaXichunBenevolence', value: 200 }],
    })
  })

  it('rejects the retired skill level adders on a phase', () => {
    const kit = assistant400001.units['400005']
    const [phase] = kit.phases
    const raw = {
      ...assistant400001,
      units: {
        ...assistant400001.units,
        '400005': { ...kit, phases: [{ ...phase, skillLevelAdders: { attack: 7, defense: 4 } }] },
      },
    }
    expect(AssistantDataSchema.safeParse(raw).success).toBe(false)
  })

  it('rejects a growth row of an unknown category', () => {
    const kit = assistant400001.units['400005']
    const [phase] = kit.phases
    const growth = {
      tracks: ['ProjectGSJiaXichunWisdom'],
      max: 1000,
      rows: [{ category: 'CRIT_UPGRADE', value: 1, conditions: [] }],
    }
    const raw = {
      ...assistant400001,
      units: { ...assistant400001.units, '400005': { ...kit, phases: [{ ...phase, growth }] } },
    }
    expect(AssistantDataSchema.safeParse(raw).success).toBe(false)
  })

  it('reads the three Pwie forms with their gifts and start buffs', () => {
    const forms = AssistantDataSchema.parse(assistant400041).units['400041']?.forms ?? []
    expect(forms.map((form) => form.key)).toEqual(['base', 'shoes', 'cape'])
    expect(forms[1]?.giftIds).toEqual([990417, 990812, 990813, 990916, 990918])
    expect(forms[2]?.phase.startBuffs).toEqual([
      { keywordId: 'Charge', stack: 4 },
      { keywordId: 'BuffetShoes' },
      { keywordId: 'BuffetGoldenClothes' },
    ])
  })

  it('rejects a row form outside base, shoes and cape', () => {
    const [row] = assistant400041.engagements
    const raw = { ...assistant400041, engagements: [{ ...row, forms: ['wings'] }] }
    expect(AssistantDataSchema.safeParse(raw).success).toBe(false)
  })

  it('reads the Camellia phase maps and the co-units with their relations', () => {
    const camellia = EnemyDataSchema.parse(enemy8044)
    expect(camellia.engagements[0]?.phaseMaps).toEqual({ '80442': 'Cp4_SheddingTears__second' })
    const signboard = EnemyDataSchema.parse(enemy8556).engagements.find((row) =>
      row.stage.endsWith('#2067035'),
    )
    expect(signboard?.coUnits?.[0]).toEqual({
      unit: 1027,
      card: { family: 'enemy', id: 8564 },
      relation: 'summons',
    })
  })

  it('reads the copied identity of envy-sin 1099', () => {
    const [phase] = EnemyDataSchema.parse(enemy1099).units['1099']?.phases ?? []
    expect(phase?.overridePersonalityId).toBe(10302)
  })

  it('reads the new story-dungeon and walpurgis label shapes', () => {
    const stages = BattleStageListSchema.parse(stageList)
    expect(stages['abbattle1-4.json#10412']?.labels).toEqual([
      { kind: 'storyDungeon', chapter: { canto: 4 }, dungeonId: 10401, stage: 54 },
    ])
    expect(stages['storybattle-event1.json#10550']?.labels?.[0]).toMatchObject({
      kind: 'walpurgis',
      anchor: { number: '5.5', part: '1' },
    })
  })
})

describe('trigger-gated skill fields', () => {
  const kit = assistant400001.units['400005']
  const [phase] = kit.phases
  const withGate = (rowPatch: object) => ({
    ...assistant400001,
    units: {
      ...assistant400001.units,
      '400005': { ...kit, phases: [{ ...phase, gatedSkillIds: [40000502] }] },
    },
    engagements: assistant400001.engagements.map((row) =>
      row.stage.endsWith('#911911') ? { ...row, ...rowPatch } : row,
    ),
  })

  it('reads gatedSkillIds on a phase and unlockedSkillIds on a row', () => {
    const group = AssistantDataSchema.parse(withGate({ unlockedSkillIds: [40000502] }))
    expect(group.units['400005']?.phases[0].gatedSkillIds).toEqual([40000502])
    expect(
      group.engagements.find((row) => row.stage.endsWith('#911911'))?.unlockedSkillIds,
    ).toEqual([40000502])
  })

  it('parses the shipped gate of 40000502 on 400005 and its unlock on row 911911 alone', () => {
    const group = AssistantDataSchema.parse(assistant400001)
    expect(group.units['400005']?.phases[0].gatedSkillIds).toEqual([40000502])
    expect(
      group.engagements
        .filter((row) => row.unlockedSkillIds !== undefined)
        .map((row) => [row.stage, row.unlockedSkillIds]),
    ).toEqual([['storybattle-cultivation.json#911911', [40000502]]])
  })

  it('rejects a non-numeric unlocked skill id', () => {
    expect(
      AssistantDataSchema.safeParse(withGate({ unlockedSkillIds: ['40000502'] })).success,
    ).toBe(false)
  })
})

describe('battle group units', () => {
  it('refuses a row whose unit the group lacks', () => {
    const raw = {
      ...enemy8063,
      engagements: enemy8063.engagements.map((row, index) =>
        index === 0 ? { ...row, unit: 8070 } : row,
      ),
    }
    const result = EnemyDataSchema.safeParse(raw)

    expect(result.success).toBe(false)
    expect(result.error?.issues).toContainEqual(
      expect.objectContaining({
        message: 'battle group has no unit 8070',
        path: ['engagements', 0, 'unit'],
      }),
    )
  })

  it('refuses a group without units', () => {
    const result = EnemyDataSchema.safeParse({ ...enemy8063, units: {}, engagements: [] })

    expect(result.success).toBe(false)
    expect(result.error?.issues.map((issue) => issue.message)).toContain(
      'battle group has no units',
    )
  })
})
