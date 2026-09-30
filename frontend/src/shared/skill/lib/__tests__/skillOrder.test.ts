import { describe, it, expect } from 'vitest'

import identity10310 from '@static/data/identity/10310.json'
import ego20102 from '@static/data/ego/20102.json'
import { skillMapWithRetiredKeys } from '@/shared/gameData'
import { orderEgoSkills, orderIdentitySkills } from '../skillOrder'

const identitySkills = skillMapWithRetiredKeys(['skill1', 'skill2', 'skill3', 'skillDef']).parse(
  identity10310.skills,
)
const egoSkills = skillMapWithRetiredKeys(['awaken', 'erosion']).parse(ego20102.skills)

describe('orderIdentitySkills', () => {
  it('orders attack before defense, then by tier, then by id', () => {
    expect(orderIdentitySkills(identitySkills).map((skill) => skill.id)).toEqual([
      '1031001',
      '1031005',
      '1031002',
      '1031006',
      '1031003',
      '1031007',
      '1031004',
      '1031008',
    ])
  })
})

describe('orderEgoSkills', () => {
  it('orders awaken before erosion', () => {
    expect(orderEgoSkills(egoSkills).map((skill) => skill.id)).toEqual(['2010211', '2010221'])
  })

  it('orders by id within a skill type and puts other types last', () => {
    const awaken = egoSkills['2010211' as keyof typeof egoSkills]
    const erosion = egoSkills['2010221' as keyof typeof egoSkills]
    if (awaken === undefined || erosion === undefined) throw new Error('fixture skill missing')
    const skills = {
      '2010299': { ...awaken, skillType: 'SKILL' },
      '2010231': erosion,
      '2010212': awaken,
      '2010211': awaken,
    } as typeof egoSkills
    expect(orderEgoSkills(skills).map((skill) => skill.id)).toEqual([
      '2010211',
      '2010212',
      '2010231',
      '2010299',
    ])
  })
})
