export function mergeSkillDataUpToLevel<T extends object>(entries: T[], level: number): T {
  const merged = {} as T
  for (let i = 0; i < level; i++) {
    Object.assign(merged, entries[i])
  }
  return merged
}
