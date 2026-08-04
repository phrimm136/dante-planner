/**
 * Tier-based passive selection.
 *
 * Entities expose passives as one array per tier, where an empty tier means
 * "inherit whatever the closest non-empty tier below provides". Identity
 * (uptie 1-4) and EGO (threadspin 1-5) share that model and differ only in the
 * element type and in the key that decides whether a higher-tier passive
 * *replaces* an effective one (hidden) or stands beside it as a dimmed preview
 * (locked) — hence `T` and the `keyOf` parameter.
 */

/**
 * Passives visible and active at `tierIndex`.
 *
 * Returns the tier array itself, walking down past empty tiers.
 */
export function selectEffectivePassives<T>(passiveList: T[][], tierIndex: number): T[] {
  for (let i = tierIndex; i >= 0; i--) {
    const tier = passiveList[i]
    if (tier && tier.length > 0) return tier
  }
  return []
}

/**
 * Passives from tiers above `tierIndex` that are previews of something new.
 *
 * A higher-tier passive whose key is already covered — by an effective passive
 * or by an earlier locked one — replaces rather than adds, so it is dropped.
 */
export function selectLockedPassives<T, K extends PropertyKey>(
  passiveList: T[][],
  tierIndex: number,
  keyOf: (passive: T) => K,
): T[] {
  const effective = selectEffectivePassives(passiveList, tierIndex)
  const effectiveSet = new Set(effective)
  const seenKeys = new Set(effective.map(keyOf))
  const locked: T[] = []

  for (let i = tierIndex + 1; i < passiveList.length; i++) {
    const tier = passiveList[i]
    if (!tier) continue

    for (const passive of tier) {
      if (effectiveSet.has(passive)) continue
      const key = keyOf(passive)
      if (seenKeys.has(key)) continue
      locked.push(passive)
      seenKeys.add(key)
    }
  }

  return locked
}
