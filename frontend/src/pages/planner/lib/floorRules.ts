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

const byPathThenCode = (a: Violation, b: Violation) => {
  if (a.path !== b.path) return a.path < b.path ? -1 : 1
  if (a.code !== b.code) return a.code < b.code ? -1 : 1
  return 0
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

function parseFloor(
  raw: unknown,
  floorIndex: number,
): { floor: FloorSelectionValue | undefined; violations: Violation[] } {
  const path = floorPath(floorIndex)
  if (!isRecord(raw)) {
    return { floor: undefined, violations: [violation('INVALID_FIELD_TYPE', path)] }
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

  return violations.length > 0 ? { floor: undefined, violations } : { floor, violations }
}

export function parseFloors(raw: unknown, floorCount: number): ParsedFloors {
  if (!Array.isArray(raw)) {
    return { floors: [], violations: [violation('INVALID_FIELD_TYPE', FLOOR_SELECTIONS)] }
  }
  const results = (raw as unknown[])
    .slice(0, floorCount)
    .map((floor, floorIndex) => parseFloor(floor, floorIndex))
  return {
    floors: results.map((result) => result.floor),
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

  const rules = (Object.keys(RULES) as FloorRuleName[]).filter((rule) =>
    table.rules[rule].includes(stage),
  )
  if (rules.length === 0) {
    return { ok: true, floors, boundaryViolations: parsed.violations }
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
