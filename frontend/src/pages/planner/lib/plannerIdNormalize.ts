import {
  GIFT_ENHANCEMENT_PREFIX_PATTERN,
  GIFT_ID_PATTERN,
  REQUIRED_EGO_TYPE,
} from '@/shared/gameData'
import { isMDPlanner } from '../types/PlannerTypes'
import type { IdMigrationEntry, IdMigrationTable } from './idMigrationTable'
import type { MDPlannerContent, SaveablePlanner } from '../types/PlannerTypes'

const ENCODED_GIFT_RE = new RegExp(`^(${GIFT_ENHANCEMENT_PREFIX_PATTERN})(${GIFT_ID_PATTERN})$`)

const GIFT_ARRAY_FIELDS = ['selectedGiftIds', 'observationGiftIds', 'comprehensiveGiftIds'] as const

type Loose = Record<string, unknown>

function isRecord(value: unknown): value is Loose {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function migrateId(id: string, entry: IdMigrationEntry | undefined): string | null {
  if (entry === undefined) return id
  if (entry.drop.includes(id)) return null
  return entry.rename[id] ?? id
}

function migrateGiftId(encoded: string, entry: IdMigrationEntry | undefined): string | null {
  const match = encoded.match(ENCODED_GIFT_RE)
  if (match === null) return encoded
  const [, prefix = '', baseId = ''] = match
  const migrated = migrateId(baseId, entry)
  return migrated === null ? null : `${prefix}${migrated}`
}

function migrateArray(raw: unknown, migrate: (item: unknown) => unknown): unknown {
  if (!Array.isArray(raw)) return raw
  const seen = new Set<unknown>()
  for (const item of raw) {
    const migrated = migrate(item)
    if (migrated !== null) seen.add(migrated)
  }
  return Array.from(seen)
}

function migrateGiftArray(raw: unknown, entry: IdMigrationEntry | undefined): unknown {
  return migrateArray(raw, (item) => (typeof item === 'string' ? migrateGiftId(item, entry) : item))
}

function migrateBuffArray(raw: unknown, entry: IdMigrationEntry | undefined): unknown {
  return migrateArray(raw, (item) => {
    if (typeof item !== 'number') return item
    const migrated = migrateId(String(item), entry)
    return migrated === null ? null : Number(migrated)
  })
}

function migrateSlot(
  egoType: string,
  slot: unknown,
  entry: IdMigrationEntry | undefined,
): { keep: boolean; value: unknown } {
  if (!isRecord(slot) || typeof slot.id !== 'string') return { keep: true, value: slot }
  const migrated = migrateId(slot.id, entry)
  if (migrated === null) return { keep: egoType === REQUIRED_EGO_TYPE, value: slot }
  return { keep: true, value: migrated === slot.id ? slot : { ...slot, id: migrated } }
}

function migrateSinnerEquipment(raw: unknown, table: IdMigrationTable): unknown {
  if (!isRecord(raw)) return raw
  const next: Loose = { ...raw }

  const { identity } = raw
  if (isRecord(identity) && typeof identity.id === 'string') {
    const renamed = table.identity?.rename[identity.id]
    if (renamed !== undefined) next.identity = { ...identity, id: renamed }
  }

  if (isRecord(raw.egos)) {
    const egos: Loose = {}
    for (const [egoType, slot] of Object.entries(raw.egos)) {
      const ego = migrateSlot(egoType, slot, table.ego)
      if (ego.keep) egos[egoType] = ego.value
    }
    next.egos = egos
  }

  return next
}

function migrateEquipment(raw: unknown, table: IdMigrationTable): unknown {
  if (!isRecord(raw)) return raw
  return Object.fromEntries(
    Object.entries(raw).map(([sinnerKey, equipment]) => [
      sinnerKey,
      migrateSinnerEquipment(equipment, table),
    ]),
  )
}

function migrateFloor(raw: unknown, table: IdMigrationTable): unknown {
  if (!isRecord(raw)) return raw
  const next: Loose = { ...raw }
  if ('giftIds' in raw) next.giftIds = migrateGiftArray(raw.giftIds, table.egoGift)
  if (typeof raw.themePackId === 'string') {
    next.themePackId = migrateId(raw.themePackId, table.themePack)
  }
  return next
}

export function normalizePlannerIds(content: Loose, table: IdMigrationTable): Loose {
  const next: Loose = { ...content }

  for (const field of GIFT_ARRAY_FIELDS) {
    if (field in content) next[field] = migrateGiftArray(content[field], table.egoGift)
  }
  if ('selectedBuffIds' in content) {
    next.selectedBuffIds = migrateBuffArray(content.selectedBuffIds, table.startBuff)
  }
  if ('equipment' in content) {
    next.equipment = migrateEquipment(content.equipment, table)
  }
  if (Array.isArray(content.floorSelections)) {
    next.floorSelections = content.floorSelections.map((floor) => migrateFloor(floor, table))
  }

  return next
}

export function withNormalizedIds(
  planner: SaveablePlanner,
  table: IdMigrationTable,
): SaveablePlanner {
  if (!isMDPlanner(planner)) return planner
  const content = normalizePlannerIds(
    planner.content as unknown as Loose,
    table,
  ) as unknown as MDPlannerContent
  return { ...planner, content }
}
