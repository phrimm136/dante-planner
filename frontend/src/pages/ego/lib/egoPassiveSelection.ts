/**
 * EGO Passive Selection
 *
 * Pure functions for choosing which EGO passives are "effective" (visible
 * normally) vs "locked" (dimmed preview of future tiers) at a given threadspin.
 *
 * Mirrors the identity uptie model with one EGO-specific twist: identity
 * dedupes locked passives by a `variant` decoded from the ID, while EGO uses
 * a `slot key` derived from the same ID (everything except the last digit).
 * Two passives that differ only in the last digit (e.g. `2040211` "active at
 * threadspin 2-4" and `2040212` "active at threadspin 5") share a slot and
 * are mutually exclusive — the higher-tier one *replaces* the lower-tier one
 * rather than appearing alongside it.
 */

/**
 * Slot key for an EGO passive — drops the trailing variant digit.
 *
 * @example
 * getEgoPassiveSlotKey('2040211') // => '204021'
 * getEgoPassiveSlotKey('2040212') // => '204021'   (same slot, different variant)
 */
export function getEgoPassiveSlotKey(passiveId: string): string {
  return passiveId.slice(0, -1)
}

/**
 * Get the effective (visible, active) EGO passives at the given threadspin.
 *
 * Empty slots inherit from the most recent non-empty slot below them,
 * so `passiveList[1]` covers threadspin 2, 3, 4 until something explicit
 * lands in `passiveList[4]`.
 */
export function getEffectiveEgoPassives(
  passiveList: string[][],
  threadspinIndex: number,
): string[] {
  for (let i = threadspinIndex; i >= 0; i--) {
    const slot = passiveList[i]
    if (slot && slot.length > 0) return slot
  }
  return []
}

/**
 * Get locked passives — those from higher tiers that aren't part of the
 * effective set. A higher-tier passive that simply *replaces* an effective
 * one (same slot key) is hidden, not shown as a dimmed preview.
 */
export function getLockedEgoPassives(
  passiveList: string[][],
  threadspinIndex: number,
): string[] {
  const effective = getEffectiveEgoPassives(passiveList, threadspinIndex)
  const effectiveSet = new Set(effective)
  const seenSlots = new Set(effective.map(getEgoPassiveSlotKey))
  const locked: string[] = []

  for (let i = threadspinIndex + 1; i < passiveList.length; i++) {
    const tier = passiveList[i]
    if (!tier) continue

    for (const passiveId of tier) {
      if (effectiveSet.has(passiveId)) continue
      const slot = getEgoPassiveSlotKey(passiveId)
      if (seenSlots.has(slot)) continue
      locked.push(passiveId)
      seenSlots.add(slot)
    }
  }

  return locked
}
