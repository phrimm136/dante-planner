/**
 * Identity Passive Selection
 *
 * Pure functions for choosing which identity passives are "effective" (visible
 * normally) vs "locked" (dimmed preview of higher upties) at a given uptie.
 *
 * Identity dedupes by the `variant` decoded from the passive ID, so a base
 * passive and its enhanced counterpart share a variant and are mutually
 * exclusive — the enhanced one *replaces* the base rather than appearing
 * alongside it.
 */

import { selectEffectivePassives, selectLockedPassives } from '@/shared/passiveSelection'
import type { IdentityData } from '../types/IdentityTypes'

type PassiveConditions = IdentityData['passives']['conditions']
type PassiveCondition = PassiveConditions[string]

/**
 * Extract passive type info from ID.
 * ID format: {identity_id:5}{type:1}{variant:1}1
 * Type: 0=battle, 1=enhanced battle, 2=support
 */
export function getPassiveInfo(passiveId: number): { type: number; variant: number } {
  const suffix = passiveId % 100
  const type = Math.floor(suffix / 10)
  const variant = suffix % 10
  return { type, variant }
}

/**
 * Get effective passives at the current uptie level.
 * Empty arrays mean "inherit from previous tier".
 */
export function getEffectivePassives(passiveList: number[][], currentUptieIndex: number): number[] {
  return selectEffectivePassives(passiveList, currentUptieIndex)
}

/**
 * Get locked passives: passives from higher tiers not available at the current
 * tier. A higher-tier passive sharing a variant with an already-shown one is
 * hidden, not previewed.
 */
export function getLockedPassives(passiveList: number[][], currentUptieIndex: number): number[] {
  return selectLockedPassives(passiveList, currentUptieIndex, (passiveId) => {
    return getPassiveInfo(passiveId).variant
  })
}

/**
 * Get the condition for a passive.
 * Enhanced passives (type=1) inherit conditions from the base (type=0) with
 * the same variant when they carry none of their own.
 */
export function getPassiveCondition(
  conditions: PassiveConditions,
  passiveId: number,
): PassiveCondition | undefined {
  const directCondition = conditions[String(passiveId)]
  if (directCondition) return directCondition

  const { type } = getPassiveInfo(passiveId)
  if (type === 1) {
    const basePassiveId = passiveId - 10
    return conditions[String(basePassiveId)]
  }

  return undefined
}
