import { describe, it, expect } from 'vitest'

import identity10310 from '@static/data/identity/10310.json'
import ego20102 from '@static/data/ego/20102.json'
import { SkillEntrySchema } from '@/shared/gameData'
import { selectSkillLevel, toSkillCardData } from '../skillLevel'

const skill3 = SkillEntrySchema.parse(identity10310.skills['1031003'])
const skill1 = SkillEntrySchema.parse(identity10310.skills['1031001'])
const awaken = SkillEntrySchema.parse(ego20102.skills['2010211'])
const erosion = SkillEntrySchema.parse(ego20102.skills['2010221'])

describe('selectSkillLevel', () => {
  it('takes the greatest defined level not above the tier', () => {
    expect(selectSkillLevel(awaken.levels, 2)).toEqual({ level: awaken.levels[0], isLocked: false })
    expect(selectSkillLevel(awaken.levels, 4)).toEqual({ level: awaken.levels[2], isLocked: false })
    expect(selectSkillLevel(awaken.levels, 5)).toEqual({ level: awaken.levels[3], isLocked: false })
  })

  it('takes the exact level when the tier defines one', () => {
    expect(selectSkillLevel(skill3.levels, 3).level.level).toBe(3)
    expect(selectSkillLevel(skill3.levels, 4).level.level).toBe(4)
  })

  it('shows the lowest defined level, locked, when no level is at or below the tier', () => {
    expect(selectSkillLevel(skill3.levels, 1)).toEqual({ level: skill3.levels[0], isLocked: true })
    expect(selectSkillLevel(skill3.levels, 2)).toEqual({ level: skill3.levels[0], isLocked: true })
  })
})

describe('toSkillCardData', () => {
  it('reads the entry fields and the level values', () => {
    const level = skill1.levels[0]
    if (level === undefined) throw new Error('fixture has no level')
    expect(toSkillCardData(skill1, level)).toEqual({
      attributeType: 'AMBER',
      atkType: 'PENETRATE',
      defaultValue: 3,
      scale: 3,
      skillLevelCorrection: 3,
      targetNum: 1,
      mpUsage: 0,
    })
  })

  it('negates the coin power of a subtracting coin', () => {
    const level = erosion.levels[0]
    if (level === undefined) throw new Error('fixture has no level')
    expect(toSkillCardData(erosion, level).scale).toBe(-12)
  })
})
