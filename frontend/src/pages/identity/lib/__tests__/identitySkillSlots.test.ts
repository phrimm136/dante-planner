import { describe, it, expect } from 'vitest'
import { getSkillSlotNumber, getSkillAttributeType } from '../identitySkillSlots'
import type { SkillSlot } from '../identitySkillSlots'
import type { IdentitySkillEntry } from '../../types/IdentityTypes'
import { SkillIdSchema } from '@/shared/gameData'
import type { SkillId } from '@/shared/gameData'

const sid = (id: string): SkillId => SkillIdSchema.parse(id)

describe('getSkillSlotNumber', () => {
  it('maps every slot to its image path number', () => {
    expect(getSkillSlotNumber('skill1')).toBe(1)
    expect(getSkillSlotNumber('skill2')).toBe(2)
    expect(getSkillSlotNumber('skill3')).toBe(3)
    expect(getSkillSlotNumber('skillDef')).toBe(4)
  })

  it('throws on a slot outside the union', () => {
    expect(() => getSkillSlotNumber('skill4' as SkillSlot)).toThrow('Unhandled union member')
  })
})

describe('getSkillAttributeType', () => {
  const entry = (id: SkillId, skillData: object[]): IdentitySkillEntry =>
    ({ id, skillData }) as IdentitySkillEntry

  const skills: Record<SkillSlot, IdentitySkillEntry[]> = {
    skill1: [entry(sid('1011401'), [{ attributeType: 'AMBER', atkType: 'SLASH' }])],
    skill2: [entry(sid('1011402'), [{ attributeType: 'VIOLET' }, { attributeType: 'AZURE' }])],
    skill3: [entry(sid('1011403'), [{}, {}, { attributeType: 'SHAMROCK' }, {}])],
    skillDef: [],
  }

  it('reads the attribute type from the first level', () => {
    expect(getSkillAttributeType(skills, 'skill1')).toBe('AMBER')
  })

  it('lets a later level override an earlier one', () => {
    expect(getSkillAttributeType(skills, 'skill2')).toBe('AZURE')
  })

  it('finds an attribute type that only appears at a higher uptie', () => {
    expect(getSkillAttributeType(skills, 'skill3')).toBe('SHAMROCK')
  })

  it('returns undefined for a slot with no entries', () => {
    expect(getSkillAttributeType(skills, 'skillDef')).toBeUndefined()
  })
})
