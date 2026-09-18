import { assertNever } from '@/lib/utils'
import type { IdentitySkillEntry } from '../types/IdentityTypes'

export type SkillSlot = 'skill1' | 'skill2' | 'skill3' | 'skillDef'

export function getSkillSlotNumber(slot: SkillSlot): number {
  switch (slot) {
    case 'skill1':
      return 1
    case 'skill2':
      return 2
    case 'skill3':
      return 3
    case 'skillDef':
      return 4
    default:
      return assertNever(slot)
  }
}

export function getSkillAttributeType(
  skills: Record<SkillSlot, IdentitySkillEntry[]>,
  slot: SkillSlot,
): string | undefined {
  const skillEntries = skills[slot]
  if (!skillEntries || skillEntries.length === 0) return undefined

  const entry = skillEntries[0]
  if (!entry) return undefined

  const merged: Record<string, unknown> = {}
  for (let i = 0; i < entry.skillData.length; i++) {
    Object.assign(merged, entry.skillData[i])
  }

  return merged.attributeType as string | undefined
}
