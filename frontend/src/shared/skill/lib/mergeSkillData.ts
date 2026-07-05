/**
 * Merge per-level skill-data entries up to (and including) the given 1-based
 * level. Earlier levels provide base values; later levels override.
 *
 * Shared by the identity (uptie 1–4) and ego (threadspin 1–5) skill-card
 * adapters, which differ only in the entry type — hence the generic `T`.
 */
export function mergeSkillDataUpToLevel<T extends object>(entries: T[], level: number): T {
  const merged = {} as T
  for (let i = 0; i < level; i++) {
    Object.assign(merged, entries[i])
  }
  return merged
}
