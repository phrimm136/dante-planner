import type { EngagementRow } from '../schemas/BattleDetailSchemas'
import { BATTLE_CASES, type BattleCase } from './battleVocabulary'

export interface SelectorRow {
  index: number
  row: EngagementRow
  suffix: number | undefined
}

export interface SelectorRowGroup {
  battleCase: BattleCase
  rows: SelectorRow[]
}

function unitPositions(rows: readonly EngagementRow[]): Map<string, Map<number, number>> {
  const byStage = new Map<string, Map<number, number>>()
  for (const row of rows) {
    const units = byStage.get(row.stage) ?? new Map<number, number>()
    if (!units.has(row.unit)) units.set(row.unit, units.size + 1)
    byStage.set(row.stage, units)
  }
  return byStage
}

export function groupSelectorRows(rows: readonly EngagementRow[]): SelectorRowGroup[] {
  const positions = unitPositions(rows)
  const indexed = rows.map((row, index): SelectorRow => {
    const units = positions.get(row.stage)
    const suffix = units !== undefined && units.size > 1 ? units.get(row.unit) : undefined
    return { index, row, suffix }
  })
  return BATTLE_CASES.flatMap((battleCase) => {
    const inCase = indexed.filter(({ row }) => row.case === battleCase)
    return inCase.length === 0 ? [] : [{ battleCase, rows: inCase }]
  })
}

export function defaultRowIndex(
  groups: readonly SelectorRowGroup[],
  arrivalMode: BattleCase,
): number | undefined {
  const arrived = groups.find((group) => group.battleCase === arrivalMode)
  return (arrived ?? groups[0])?.rows[0]?.index
}

export function firstMapName(groups: readonly SelectorRowGroup[]): string | undefined {
  return groups[0]?.rows[0]?.row.mapName
}
