import type { EngagementRow } from '../schemas/BattleDetailSchemas'
import { BATTLE_CASES, type BattleCase } from './battleVocabulary'

export interface SelectorRow {
  index: number
  row: EngagementRow
}

export interface SelectorRowGroup {
  battleCase: BattleCase
  rows: SelectorRow[]
}

export interface SelectorEntry {
  index: number
  label: string
  suffix: number | undefined
}

export function groupSelectorRows(rows: readonly EngagementRow[]): SelectorRowGroup[] {
  const indexed = rows.map((row, index): SelectorRow => ({ index, row }))
  return BATTLE_CASES.flatMap((battleCase) => {
    const inCase = indexed.filter(({ row }) => row.case === battleCase)
    return inCase.length === 0 ? [] : [{ battleCase, rows: inCase }]
  })
}

export function selectorEntries(
  rows: readonly SelectorRow[],
  labelOf: (row: EngagementRow) => string,
  contentOf: (row: EngagementRow) => string,
): SelectorEntry[] {
  const contentsByLabel = new Map<string, string[]>()
  const firsts: { index: number; label: string; content: string }[] = []
  for (const { index, row } of rows) {
    const label = labelOf(row)
    const content = contentOf(row)
    const contents = contentsByLabel.get(label) ?? []
    if (contents.includes(content)) continue
    contents.push(content)
    contentsByLabel.set(label, contents)
    firsts.push({ index, label, content })
  }
  return firsts.map(({ index, label, content }) => {
    const contents = contentsByLabel.get(label) ?? []
    return {
      index,
      label,
      suffix: contents.length > 1 ? contents.indexOf(content) + 1 : undefined,
    }
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
