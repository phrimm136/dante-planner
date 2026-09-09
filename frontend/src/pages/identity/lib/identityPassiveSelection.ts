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
import type { PassiveId } from '@/shared/gameData'

/**
 * Extract passive type info from ID.
 * ID format: {identity_id:5}{type:1}{variant:1}1
 * Type: 0=battle, 1=enhanced battle, 2=support, 3=enhanced support
 */
export function getPassiveInfo(passiveId: PassiveId): { type: number; variant: number } {
  const suffix = passiveId.slice(-2)
  return { type: Number(suffix[0]), variant: Number(suffix[1]) }
}

/**
 * Get effective passives at the current uptie level.
 * Empty arrays mean "inherit from previous tier".
 */
export function getEffectivePassives(
  passiveList: PassiveId[][],
  currentUptieIndex: number,
): PassiveId[] {
  return selectEffectivePassives(passiveList, currentUptieIndex)
}

/**
 * Get locked passives: passives from higher tiers not available at the current
 * tier. A higher-tier passive sharing a variant with an already-shown one is
 * hidden, not previewed.
 */
export function getLockedPassives(
  passiveList: PassiveId[][],
  currentUptieIndex: number,
): PassiveId[] {
  return selectLockedPassives(passiveList, currentUptieIndex, (passiveId) => {
    return getPassiveInfo(passiveId).variant
  })
}
