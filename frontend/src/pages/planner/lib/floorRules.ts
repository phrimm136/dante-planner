import { DUNGEON_IDX, FLOOR_RULE_TABLE } from '@/shared/gameData'
import type { FloorRuleName, FloorRuleStage, FloorRuleTable, MDCategory } from '@/shared/gameData'

export type BeErrorCode =
  | 'INVALID_FIELD_TYPE'
  | 'FLOOR_MISSING_THEME_PACK'
  | 'FLOOR_DUPLICATE_THEME_PACK'
  | 'INVALID_SEQUENCE'
  | 'VALUE_OUT_OF_RANGE'
  | 'DUPLICATE_VALUE'

export type Violation = { code: BeErrorCode; path: string }

export type FloorSelectionValue = {
  themePackId?: string
  difficulty?: number
  giftIds: readonly string[]
}

export type ParsedFloors = {
  floors: (FloorSelectionValue | undefined)[]
  salvage: (FloorSelectionValue | undefined)[]
  violations: Violation[]
}

export type Admission =
  | {
      ok: true
      floors: readonly FloorSelectionValue[]
      boundaryViolations: readonly Violation[]
    }
  | { ok: false; violations: readonly Violation[] }

const FLOOR_SELECTIONS = 'floorSelections'

const floorPath = (floorIndex: number) => `${FLOOR_SELECTIONS}[${floorIndex}]`

const violation = (code: BeErrorCode, path: string): Violation => ({ code, path })

const FLOOR_INDEX_PREFIX = new RegExp(`^${FLOOR_SELECTIONS}\\[(\\d+)\\]`)

function splitFloorPath(path: string): [number, string] {
  const match = FLOOR_INDEX_PREFIX.exec(path)
  return match ? [Number(match[1]), path.slice(match[0].length)] : [-1, path]
}

const byPathThenCode = (a: Violation, b: Violation) => {
  const [aFloor, aRest] = splitFloorPath(a.path)
  const [bFloor, bRest] = splitFloorPath(b.path)
  if (aFloor !== bFloor) return aFloor - bFloor
  if (aRest !== bRest) return aRest < bRest ? -1 : 1
  if (a.code !== b.code) return a.code < b.code ? -1 : 1
  return 0
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const scalarText = (value: unknown): string | undefined =>
  typeof value === 'string' ? value : typeof value === 'number' ? String(value) : undefined

const EMPTY_SALVAGE: FloorSelectionValue = { giftIds: [] }

function salvageFloor(raw: Record<string, unknown>): FloorSelectionValue {
  const themePackId = scalarText(raw.themePackId)
  const giftIds: unknown[] = Array.isArray(raw.giftIds) ? raw.giftIds : []
  return {
    ...(themePackId ? { themePackId } : {}),
    ...(Number.isInteger(raw.difficulty) ? { difficulty: raw.difficulty as number } : {}),
    giftIds: giftIds.flatMap((giftId) => scalarText(giftId) ?? []),
  }
}

type ParsedFloor = {
  floor: FloorSelectionValue | undefined
  salvage: FloorSelectionValue | undefined
  violations: Violation[]
}

function parseFloor(raw: unknown, floorIndex: number): ParsedFloor {
  const path = floorPath(floorIndex)
  if (!isRecord(raw)) {
    return {
      floor: undefined,
      salvage: EMPTY_SALVAGE,
      violations: [violation('INVALID_FIELD_TYPE', path)],
    }
  }

  const violations: Violation[] = []
  const floor: FloorSelectionValue = { giftIds: [] }

  const themePackId = raw.themePackId
  if (typeof themePackId === 'string') {
    if (themePackId !== '') floor.themePackId = themePackId
  } else if (themePackId !== undefined && themePackId !== null) {
    violations.push(violation('INVALID_FIELD_TYPE', `${path}.themePackId`))
  }

  const difficulty = raw.difficulty
  if (Number.isInteger(difficulty)) {
    floor.difficulty = difficulty as number
  } else if (difficulty !== undefined) {
    violations.push(violation('INVALID_FIELD_TYPE', `${path}.difficulty`))
  }

  const giftIds = raw.giftIds
  if (Array.isArray(giftIds)) {
    const elements: unknown[] = giftIds
    elements.forEach((giftId, giftIndex) => {
      if (typeof giftId !== 'string') {
        violations.push(violation('INVALID_FIELD_TYPE', `${path}.giftIds[${giftIndex}]`))
      }
    })
    floor.giftIds = elements.filter((giftId): giftId is string => typeof giftId === 'string')
  } else if (giftIds !== undefined) {
    violations.push(violation('INVALID_FIELD_TYPE', `${path}.giftIds`))
  }

  return violations.length > 0
    ? { floor: undefined, salvage: salvageFloor(raw), violations }
    : { floor, salvage: undefined, violations }
}

export function parseFloors(raw: unknown, floorCount: number): ParsedFloors {
  if (!Array.isArray(raw)) {
    return {
      floors: [],
      salvage: [],
      violations: [violation('INVALID_FIELD_TYPE', FLOOR_SELECTIONS)],
    }
  }
  const results = (raw as unknown[])
    .slice(0, floorCount)
    .map((floor, floorIndex) => parseFloor(floor, floorIndex))
  return {
    floors: results.map((result) => result.floor),
    salvage: results.map((result) => result.salvage),
    violations: results.flatMap((result) => result.violations).sort(byPathThenCode),
  }
}

type RuleCheck = (
  floors: readonly FloorSelectionValue[],
  floorCount: number,
  allowed: readonly (readonly number[])[],
) => Violation[]

const everyFloorPresent: RuleCheck = (floors, floorCount) =>
  Array.from({ length: Math.max(floorCount - floors.length, 0) }, (_, offset) =>
    violation('FLOOR_MISSING_THEME_PACK', floorPath(floors.length + offset)),
  )

const themePackPresent: RuleCheck = (floors) =>
  floors.flatMap((floor, floorIndex) =>
    floor.themePackId === undefined
      ? [violation('FLOOR_MISSING_THEME_PACK', floorPath(floorIndex))]
      : [],
  )

const notRepeated: RuleCheck = (floors) =>
  floors.flatMap((floor, floorIndex) =>
    floor.themePackId !== undefined &&
    floors.slice(0, floorIndex).some((earlier) => earlier.themePackId === floor.themePackId)
      ? [violation('FLOOR_DUPLICATE_THEME_PACK', `${floorPath(floorIndex)}.themePackId`)]
      : [],
  )

const sequence: RuleCheck = (floors) =>
  floors.flatMap((floor, floorIndex) =>
    floorIndex > 0 &&
    floor.themePackId !== undefined &&
    floors[floorIndex - 1]?.themePackId === undefined
      ? [violation('INVALID_SEQUENCE', floorPath(floorIndex))]
      : [],
  )

const difficultyInRange: RuleCheck = (floors, _floorCount, allowed) =>
  floors.flatMap((floor, floorIndex) =>
    floor.difficulty !== undefined && (allowed[floorIndex] ?? []).includes(floor.difficulty)
      ? []
      : [violation('VALUE_OUT_OF_RANGE', `${floorPath(floorIndex)}.difficulty`)],
  )

const noNormalAfterHard: RuleCheck = (floors, _floorCount, allowed) =>
  floors.flatMap((floor, floorIndex) =>
    (allowed[floorIndex] ?? []).includes(DUNGEON_IDX.NORMAL) &&
    floor.difficulty === DUNGEON_IDX.NORMAL &&
    floors.slice(0, floorIndex).some((earlier) => earlier.difficulty === DUNGEON_IDX.HARD)
      ? [violation('INVALID_SEQUENCE', `${floorPath(floorIndex)}.difficulty`)]
      : [],
  )

const giftUnique: RuleCheck = (floors) =>
  floors.flatMap((floor, floorIndex) =>
    new Set(floor.giftIds).size === floor.giftIds.length
      ? []
      : [violation('DUPLICATE_VALUE', `${floorPath(floorIndex)}.giftIds`)],
  )

const RULES: Record<FloorRuleName, RuleCheck> = {
  everyFloorPresent,
  themePackPresent,
  notRepeated,
  sequence,
  difficultyInRange,
  noNormalAfterHard,
  giftUnique,
}

export const CHECKED_FLOOR_RULES = Object.keys(RULES) as FloorRuleName[]

export function admitFloors(
  parsed: ParsedFloors,
  category: MDCategory,
  stage: FloorRuleStage,
  table: FloorRuleTable = FLOOR_RULE_TABLE,
): Admission {
  const { floorCount, difficulties } = table.categories[category]
  const floors = parsed.floors
    .slice(0, floorCount)
    .filter((floor): floor is FloorSelectionValue => floor !== undefined)

  const rules = CHECKED_FLOOR_RULES.filter((rule) => table.rules[rule].includes(stage))
  if (rules.length === 0) {
    const indexed = parsed.floors
      .slice(0, floorCount)
      .map((floor, floorIndex) => floor ?? parsed.salvage[floorIndex])
      .filter((floor): floor is FloorSelectionValue => floor !== undefined)
    return { ok: true, floors: indexed, boundaryViolations: parsed.violations }
  }
  if (parsed.violations.length > 0) {
    return { ok: false, violations: parsed.violations }
  }

  const violations = rules
    .flatMap((rule) => RULES[rule](floors, floorCount, difficulties))
    .sort(byPathThenCode)

  return violations.length > 0
    ? { ok: false, violations }
    : { ok: true, floors, boundaryViolations: [] }
}

export type EditorFloor = {
  themePackId?: string | null | undefined
  difficulty?: number | undefined
}

const PROBE_PACK = 'probe'

const toFloorValue = (floor: EditorFloor | undefined): FloorSelectionValue => ({
  ...(floor?.themePackId ? { themePackId: floor.themePackId } : {}),
  ...(floor?.difficulty !== undefined ? { difficulty: floor.difficulty } : {}),
  giftIds: [],
})

const reportsAt = (violations: readonly Violation[], path: string) =>
  violations.some((found) => found.path === path)

export function normalAllowedAt(
  floors: readonly EditorFloor[],
  category: MDCategory,
  floorIndex: number,
  table: FloorRuleTable = FLOOR_RULE_TABLE,
): boolean {
  const { floorCount, difficulties } = table.categories[category]
  const probe = [
    ...Array.from({ length: floorIndex }, (_, i) => toFloorValue(floors[i])),
    { difficulty: DUNGEON_IDX.NORMAL, giftIds: [] },
  ]
  const path = `${floorPath(floorIndex)}.difficulty`
  return [difficultyInRange, noNormalAfterHard].every(
    (rule) => !reportsAt(rule(probe, floorCount, difficulties), path),
  )
}

export function packSelectableAt(floors: readonly EditorFloor[], floorIndex: number): boolean {
  const earlier = Array.from({ length: floorIndex }, (_, i) => toFloorValue(floors[i]))
  const probe = [...earlier, { themePackId: PROBE_PACK, giftIds: [] }]
  return !reportsAt(sequence(probe, probe.length, []), floorPath(floorIndex))
}

export function violationsForCategory(
  floors: readonly EditorFloor[],
  category: MDCategory,
  table: FloorRuleTable = FLOOR_RULE_TABLE,
): readonly Violation[] {
  const admission = admitFloors(
    { floors: floors.map(toFloorValue), salvage: [], violations: [] },
    category,
    'publish',
    table,
  )
  return admission.ok ? [] : admission.violations
}

export function packIdsUsedElsewhere(
  floors: readonly EditorFloor[],
  category: MDCategory,
  floorIndex: number,
  table: FloorRuleTable = FLOOR_RULE_TABLE,
): string[] {
  return floors
    .slice(0, table.categories[category].floorCount)
    .map(toFloorValue)
    .flatMap((floor, i) =>
      i !== floorIndex && floor.themePackId !== undefined ? [floor.themePackId] : [],
    )
}
