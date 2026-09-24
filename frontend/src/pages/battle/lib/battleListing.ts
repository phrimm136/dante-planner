import type { BattleNameList } from '../schemas/BattleSpecSchemas'

export function buildBattleSearchTerms(id: string, names: BattleNameList): string[] {
  return [(names[id] ?? '').toLowerCase()]
}

export function compareBattleIds(a: { id: string }, b: { id: string }): number {
  return Number(a.id) - Number(b.id)
}
