import { describe, it, expect } from 'vitest'

import identity10310 from '@static/data/identity/10310.json'
import { SkillEntrySchema, skillMapWithRetiredKeys } from '../schemas/SkillSchemas'

const IdentitySkills = skillMapWithRetiredKeys(['skill1', 'skill2', 'skill3', 'skillDef'])

describe('skillMapWithRetiredKeys', () => {
  it('drops the retired keys and keeps every skill entry', () => {
    expect(Object.keys(IdentitySkills.parse(identity10310.skills)).sort()).toEqual([
      '1031001',
      '1031002',
      '1031003',
      '1031004',
      '1031005',
      '1031006',
      '1031007',
      '1031008',
    ])
  })

  it('accepts a record without the retired keys', () => {
    const { skill1: _1, skill2: _2, skill3: _3, skillDef: _4, ...map } = identity10310.skills
    expect(IdentitySkills.safeParse(map).success).toBe(true)
  })

  it('rejects a key that is not a skill id', () => {
    expect(
      IdentitySkills.safeParse({ ...identity10310.skills, skill4: identity10310.skills['1031001'] })
        .success,
    ).toBe(false)
  })
})

describe('SkillEntrySchema', () => {
  it('rejects an entry without levels', () => {
    expect(
      SkillEntrySchema.safeParse({ ...identity10310.skills['1031001'], levels: [] }).success,
    ).toBe(false)
  })

  it('rejects a linked part type outside AB_PART_TYPE', () => {
    const skill = identity10310.skills['1031001']
    expect(SkillEntrySchema.safeParse({ ...skill, linkedPartTypes: ['BODY'] }).success).toBe(true)
    expect(SkillEntrySchema.safeParse({ ...skill, linkedPartTypes: ['WING'] }).success).toBe(false)
  })

  it('rejects an unknown field', () => {
    expect(
      SkillEntrySchema.safeParse({ ...identity10310.skills['1031001'], slot: 1 }).success,
    ).toBe(false)
  })
})
