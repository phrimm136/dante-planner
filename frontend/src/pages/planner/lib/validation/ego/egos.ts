import { EGO_TYPES, MAX_ENTITY_TIER, MIN_ENTITY_TIER, REQUIRED_EGO_TYPE } from '@/shared/gameData'
import {
  boundedIntegerViolations,
  invalidFieldType,
  isAbsent,
  isBlank,
  isMissingText,
  isRecord,
} from '../boundary'
import type { Bounds, InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { extendPath, field } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { belongsToSinner } from '../sinnerScopedIds'
import { byPathThenCode } from '../violation'

const EGOS = 'egos'
const ID = 'id'
const THREADSPIN = 'threadspin'

const THREADSPIN_BOUNDS: Bounds = { min: MIN_ENTITY_TIER.ego, max: MAX_ENTITY_TIER.ego }

const KNOWN_EGO_TYPES: ReadonlySet<string> = new Set(EGO_TYPES)

export type EgoViolation =
  | InvalidFieldType
  | {
      code: Codes<
        'MISSING_EGO_ZAYIN' | 'INVALID_EGO_TYPE' | 'MISMATCHED_EGO' | 'OUT_OF_RANGE_EGO_THREADSPIN'
      >
      path: ContentPath
    }
  | { code: Codes<'UNKNOWN_EGO'>; path: ContentPath; params: { id: string } }

export type EquippedEgoValue = {
  egoType: string
  id: string
  sinnerKey: string
  path: ContentPath
  threadspin: unknown
  threadspinPath: ContentPath
}

type SlotOwner = { sinnerKey: string; path: ContentPath }

export type ParsedEgos = {
  egos: readonly EquippedEgoValue[]
  violations: readonly EgoViolation[]
}

export type EgoReferenceData = { hasEgo: (egoId: string) => boolean }

type SlotResult = { egos: EquippedEgoValue[]; violations: EgoViolation[] }

const NO_SLOT: SlotResult = { egos: [], violations: [] }

const missingZayin = (path: ContentPath): EgoViolation => ({ code: 'MISSING_EGO_ZAYIN', path })

const egoTypeViolations = (egos: Record<string, unknown>, path: ContentPath): EgoViolation[] =>
  Object.keys(egos)
    .filter((egoType) => !KNOWN_EGO_TYPES.has(egoType))
    .map((egoType) => ({ code: 'INVALID_EGO_TYPE', path: extendPath(path, field(egoType)) }))

function requiredEgoViolations(egos: Record<string, unknown>, path: ContentPath): EgoViolation[] {
  const slotPath = extendPath(path, field(REQUIRED_EGO_TYPE))
  const ego = egos[REQUIRED_EGO_TYPE]
  if (isAbsent(ego)) return [missingZayin(slotPath)]
  if (!isRecord(ego)) return [invalidFieldType(slotPath)]
  return isMissingText(ego[ID]) ? [missingZayin(extendPath(slotPath, field(ID)))] : []
}

function equippedEgo(
  egoType: string,
  id: string,
  ego: Record<string, unknown>,
  { sinnerKey, path }: SlotOwner,
): EquippedEgoValue {
  const slotPath = extendPath(path, field(egoType))
  return {
    egoType,
    id,
    sinnerKey,
    path: extendPath(slotPath, field(ID)),
    threadspin: ego[THREADSPIN],
    threadspinPath: extendPath(slotPath, field(THREADSPIN)),
  }
}

function parseSlot(egoType: string, ego: unknown, owner: SlotOwner): SlotResult {
  if (!isRecord(ego)) return NO_SLOT
  const id = ego[ID]
  if (typeof id === 'string') {
    return isBlank(id) ? NO_SLOT : { egos: [equippedEgo(egoType, id, ego, owner)], violations: [] }
  }
  const idPath = extendPath(owner.path, field(egoType), field(ID))
  return isAbsent(id) ? NO_SLOT : { egos: [], violations: [invalidFieldType(idPath)] }
}

function parseEgoRecord(egos: Record<string, unknown>, owner: SlotOwner): ParsedEgos {
  const { path } = owner
  const slots = Object.entries(egos).map(([egoType, ego]) => parseSlot(egoType, ego, owner))
  return {
    egos: slots.flatMap((slot) => slot.egos),
    violations: [
      ...egoTypeViolations(egos, path),
      ...requiredEgoViolations(egos, path),
      ...slots.flatMap((slot) => slot.violations),
    ].sort(byPathThenCode),
  }
}

export function parseEgos(
  sinnerEquipment: Record<string, unknown>,
  sinnerPath: ContentPath,
  sinnerKey: string,
): ParsedEgos {
  const egos = sinnerEquipment[EGOS]
  const path = extendPath(sinnerPath, field(EGOS))
  if (isRecord(egos)) return parseEgoRecord(egos, { sinnerKey, path })
  return { egos: [], violations: [isAbsent(egos) ? missingZayin(path) : invalidFieldType(path)] }
}

function equippedEgoViolations(ego: EquippedEgoValue, data: EgoReferenceData): EgoViolation[] {
  if (!data.hasEgo(ego.id)) {
    return [{ code: 'UNKNOWN_EGO', path: ego.path, params: { id: ego.id } }]
  }
  if (!belongsToSinner(ego.id, ego.sinnerKey)) return [{ code: 'MISMATCHED_EGO', path: ego.path }]
  return boundedIntegerViolations(
    ego.threadspin,
    ego.threadspinPath,
    THREADSPIN_BOUNDS,
    'OUT_OF_RANGE_EGO_THREADSPIN',
  )
}

export const egoRuleViolations = (parsed: ParsedEgos, data: EgoReferenceData): EgoViolation[] =>
  parsed.egos.flatMap((ego) => equippedEgoViolations(ego, data)).sort(byPathThenCode)
