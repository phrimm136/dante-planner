export function selectEffectivePassives<T>(passiveList: T[][], tierIndex: number): T[] {
  for (let i = tierIndex; i >= 0; i--) {
    const tier = passiveList[i]
    if (tier && tier.length > 0) return tier
  }
  return []
}

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
