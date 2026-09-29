import { DUNGEON_IDX, FLOOR_RULE_NAMES, FLOOR_RULE_TABLE } from '@/shared/gameData'
import type {
  DungeonIdx,
  FloorRuleName,
  FloorRuleStage,
  FloorRuleTable,
  MDCategory,
} from '@/shared/gameData'
import { contentPath, extendPath, field, index } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { byPathThenCode } from '../violation'
import type { FloorRuleCode } from '../violation'
import { firstIndexOf } from './positions'

export type CategoryFloors = FloorRuleTable['categories'][MDCategory]

export type FloorSelectionValue = {
  themePackId?: string
  difficulty?: number
  giftIds: readonly string[]
}

export type FloorViolation =
  | { code: Exclude<FloorRuleCode, 'VALUE_OUT_OF_RANGE'>; path: ContentPath }
  | { code: 'VALUE_OUT_OF_RANGE'; path: ContentPath; allowed: readonly DungeonIdx[] }

export type ParsedFloor =
  | { kind: 'accepted'; floor: FloorSelectionValue }
  | { kind: 'rejected'; salvage: FloorSelectionValue }

export type ParsedFloors = {
  layout: CategoryFloors
  floors: readonly ParsedFloor[]
  violations: readonly FloorViolation[]
}

export type Admission =
  | {
      ok: true
      floors: readonly FloorSelectionValue[]
      boundaryViolations: readonly FloorViolation[]
    }
  | { ok: false; violations: readonly FloorViolation[] }

const FLOOR_SELECTIONS = 'floorSelections'
const THEME_PACK_ID = 'themePackId'
const DIFFICULTY = 'difficulty'
const GIFT_IDS = 'giftIds'

const FLOOR_SELECTIONS_PATH = contentPath(field(FLOOR_SELECTIONS))

const floorPath = (floorIndex: number): ContentPath =>
  extendPath(FLOOR_SELECTIONS_PATH, index(floorIndex))

const floorFieldPath = (floorIndex: number, name: string): ContentPath =>
  extendPath(floorPath(floorIndex), field(name))

export const themePackPath = (floorIndex: number) => floorFieldPath(floorIndex, THEME_PACK_ID)

export const giftIdPath = (floorIndex: number, giftIndex: number) =>
  extendPath(floorFieldPath(floorIndex, GIFT_IDS), index(giftIndex))

const violation = (
  code: Exclude<FloorRuleCode, 'VALUE_OUT_OF_RANGE'>,
  path: ContentPath,
): FloorViolation => ({ code, path })

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const numberText = (value: unknown): string | undefined =>
  typeof value === 'number' ? String(value) : undefined

const giftText = (value: unknown): string | undefined =>
  typeof value === 'string' ? value : numberText(value)

const EMPTY_SALVAGE: FloorSelectionValue = { giftIds: [] }

type FieldResult<T> = { value: T; violations: FloorViolation[] }

const fieldValue = <T>(value: T): FieldResult<T> => ({ value, violations: [] })

const fieldRejected = <T>(value: T, path: ContentPath): FieldResult<T> => ({
  value,
  violations: [violation('INVALID_FIELD_TYPE', path)],
})

function parseThemePackId(raw: unknown, path: ContentPath): FieldResult<string | undefined> {
  if (typeof raw === 'string') return fieldValue(raw === '' ? undefined : raw)
  if (raw === undefined || raw === null) return fieldValue(undefined)
  return fieldRejected(numberText(raw), path)
}

function parseDifficulty(raw: unknown, path: ContentPath): FieldResult<number | undefined> {
  if (Number.isInteger(raw)) return fieldValue(raw as number)
  if (raw === undefined) return fieldValue(undefined)
  return fieldRejected(undefined, path)
}

function parseGiftIds(raw: unknown, path: ContentPath): FieldResult<string[]> {
  if (raw === undefined) return fieldValue([])
  if (!Array.isArray(raw)) return fieldRejected([], path)
  const elements: unknown[] = raw
  return {
    value: elements.flatMap((giftId) => giftText(giftId) ?? []),
    violations: elements.flatMap((giftId, giftIndex) =>
      typeof giftId === 'string'
        ? []
        : [violation('INVALID_FIELD_TYPE', extendPath(path, index(giftIndex)))],
    ),
  }
}

type FloorResult = { floor: ParsedFloor; violations: FloorViolation[] }

function parseFloor(raw: unknown, floorIndex: number): FloorResult {
  if (!isRecord(raw)) {
    return {
      floor: { kind: 'rejected', salvage: EMPTY_SALVAGE },
      violations: [violation('INVALID_FIELD_TYPE', floorPath(floorIndex))],
    }
  }

  const themePackId = parseThemePackId(raw.themePackId, themePackPath(floorIndex))
  const difficulty = parseDifficulty(raw.difficulty, floorFieldPath(floorIndex, DIFFICULTY))
  const giftIds = parseGiftIds(raw.giftIds, floorFieldPath(floorIndex, GIFT_IDS))
  const violations = [...themePackId.violations, ...difficulty.violations, ...giftIds.violations]
  const floor: FloorSelectionValue = {
    ...(themePackId.value !== undefined ? { themePackId: themePackId.value } : {}),
    ...(difficulty.value !== undefined ? { difficulty: difficulty.value } : {}),
    giftIds: giftIds.value,
  }

  return {
    floor:
      violations.length > 0 ? { kind: 'rejected', salvage: floor } : { kind: 'accepted', floor },
    violations,
  }
}

export function parseFloors(raw: unknown, layout: CategoryFloors): ParsedFloors {
  if (!Array.isArray(raw)) {
    return {
      layout,
      floors: [],
      violations: [violation('INVALID_FIELD_TYPE', FLOOR_SELECTIONS_PATH)],
    }
  }
  const results = (raw as unknown[])
    .slice(0, layout.floorCount)
    .map((floor, floorIndex) => parseFloor(floor, floorIndex))
  return {
    layout,
    floors: results.map((result) => result.floor),
    violations: results.flatMap((result) => result.violations).sort(byPathThenCode),
  }
}

export const acceptedFloors = (parsed: ParsedFloors): FloorSelectionValue[] =>
  parsed.floors.flatMap((floor) => (floor.kind === 'accepted' ? [floor.floor] : []))

const indexedFloors = (parsed: ParsedFloors): FloorSelectionValue[] =>
  parsed.floors.map((floor) => (floor.kind === 'accepted' ? floor.floor : floor.salvage))

type FloorPlan = { floors: readonly FloorSelectionValue[]; layout: CategoryFloors }

type RuleCheck = (plan: FloorPlan) => FloorViolation[]

const allowedAt = (layout: CategoryFloors, floorIndex: number): readonly DungeonIdx[] =>
  layout.difficulties[floorIndex] ?? []

const isHard = (floor: { difficulty?: number }) => floor.difficulty === DUNGEON_IDX.HARD

const allows = (allowed: readonly number[], difficulty: number | undefined) =>
  difficulty !== undefined && allowed.includes(difficulty)

const previousFloorHasPack = (floors: readonly FloorSelectionValue[], floorIndex: number) =>
  floorIndex === 0 || floors[floorIndex - 1]?.themePackId !== undefined

const everyFloorPresent: RuleCheck = ({ floors, layout }) =>
  Array.from({ length: layout.floorCount - floors.length }, (_, offset) =>
    violation('FLOOR_MISSING_THEME_PACK', floorPath(floors.length + offset)),
  )

const themePackPresent: RuleCheck = ({ floors }) =>
  floors.flatMap((floor, floorIndex) =>
    floor.themePackId === undefined
      ? [violation('FLOOR_MISSING_THEME_PACK', floorPath(floorIndex))]
      : [],
  )

const notRepeated: RuleCheck = ({ floors }) => {
  const firstFloorOf = firstIndexOf(floors.map((floor) => floor.themePackId))
  return floors.flatMap((floor, floorIndex) =>
    floor.themePackId !== undefined && firstFloorOf.get(floor.themePackId) !== floorIndex
      ? [violation('FLOOR_DUPLICATE_THEME_PACK', themePackPath(floorIndex))]
      : [],
  )
}

const sequence: RuleCheck = ({ floors }) =>
  floors.flatMap((floor, floorIndex) =>
    floor.themePackId !== undefined && !previousFloorHasPack(floors, floorIndex)
      ? [violation('INVALID_SEQUENCE', floorPath(floorIndex))]
      : [],
  )

const difficultyInRange: RuleCheck = ({ floors, layout }) =>
  floors.flatMap((floor, floorIndex) => {
    const allowed = allowedAt(layout, floorIndex)
    return allows(allowed, floor.difficulty)
      ? []
      : [
          {
            code: 'VALUE_OUT_OF_RANGE' as const,
            path: floorFieldPath(floorIndex, DIFFICULTY),
            allowed,
          },
        ]
  })

const noNormalAfterHard: RuleCheck = ({ floors, layout }) => {
  const firstHard = floors.findIndex(isHard)
  return floors.flatMap((floor, floorIndex) =>
    firstHard !== -1 &&
    firstHard < floorIndex &&
    floor.difficulty === DUNGEON_IDX.NORMAL &&
    allowedAt(layout, floorIndex).includes(DUNGEON_IDX.NORMAL)
      ? [violation('INVALID_SEQUENCE', floorFieldPath(floorIndex, DIFFICULTY))]
      : [],
  )
}

const giftUnique: RuleCheck = ({ floors }) =>
  floors.flatMap((floor, floorIndex) =>
    new Set(floor.giftIds).size === floor.giftIds.length
      ? []
      : [violation('DUPLICATE_VALUE', floorFieldPath(floorIndex, GIFT_IDS))],
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

const requiredRules = (stage: FloorRuleStage, table: FloorRuleTable): RuleCheck[] =>
  FLOOR_RULE_NAMES.filter((rule) => table.rules[rule].includes(stage)).map((rule) => RULES[rule])

const ruleViolations = (plan: FloorPlan, rules: readonly RuleCheck[]): FloorViolation[] =>
  rules.flatMap((rule) => rule(plan)).sort(byPathThenCode)

export function admitFloors(
  parsed: ParsedFloors,
  stage: FloorRuleStage,
  table: FloorRuleTable = FLOOR_RULE_TABLE,
): Admission {
  const rules = requiredRules(stage, table)
  if (rules.length === 0) {
    return { ok: true, floors: indexedFloors(parsed), boundaryViolations: parsed.violations }
  }
  if (parsed.violations.length > 0) {
    return { ok: false, violations: parsed.violations }
  }

  const floors = acceptedFloors(parsed)
  const violations = ruleViolations({ floors, layout: parsed.layout }, rules)
  return violations.length > 0
    ? { ok: false, violations }
    : { ok: true, floors, boundaryViolations: [] }
}

export type EditorFloor = {
  themePackId?: string | null | undefined
  difficulty?: number | undefined
}

const toFloorValue = (floor: EditorFloor | undefined): FloorSelectionValue => ({
  ...(floor?.themePackId ? { themePackId: floor.themePackId } : {}),
  ...(floor?.difficulty !== undefined ? { difficulty: floor.difficulty } : {}),
  giftIds: [],
})

export function normalAllowedAt(
  floors: readonly EditorFloor[],
  category: MDCategory,
  floorIndex: number,
  table: FloorRuleTable = FLOOR_RULE_TABLE,
): boolean {
  return (
    allowedAt(table.categories[category], floorIndex).includes(DUNGEON_IDX.NORMAL) &&
    !floors.slice(0, floorIndex).map(toFloorValue).some(isHard)
  )
}

export function packSelectableAt(floors: readonly EditorFloor[], floorIndex: number): boolean {
  return previousFloorHasPack(floors.slice(0, floorIndex).map(toFloorValue), floorIndex)
}

export function violationsForCategory(
  floors: readonly EditorFloor[],
  category: MDCategory,
  table: FloorRuleTable = FLOOR_RULE_TABLE,
): readonly FloorViolation[] {
  const layout = table.categories[category]
  return ruleViolations(
    { floors: floors.slice(0, layout.floorCount).map(toFloorValue), layout },
    requiredRules('publish', table),
  )
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
