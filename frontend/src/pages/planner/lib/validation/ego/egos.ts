import { MAX_ENTITY_TIER, MIN_ENTITY_TIER } from '@/shared/gameData'
import { outOfRangeViolations } from '../boundary'
import type { Bounds } from '../boundary'
import type { Codes } from '../codes'
import { extendPath, field } from '../contentPath'
import type { ContentPath } from '../contentPath'
import type { SinnerEquipmentValue } from '../contentShape'
import type { SinnerEntry } from '../sinnerEntries'
import { belongsToSinner } from '../sinnerScopedIds'
import { byPathThenCode } from '../violation'

const EGOS = 'egos'
const ID = 'id'
const THREADSPIN = 'threadspin'

const THREADSPIN_BOUNDS: Bounds = { min: MIN_ENTITY_TIER.ego, max: MAX_ENTITY_TIER.ego }

export type EgoViolation =
  | {
      code: Codes<
        'MISMATCHED_EGO' | 'OUT_OF_RANGE_EGO_THREADSPIN' | 'MISSING_EGO_THREADSPIN_CEILING'
      >
      path: ContentPath
    }
  | { code: Codes<'UNKNOWN_EGO'>; path: ContentPath; params: { id: string } }

export type EgoReferenceData = {
  hasEgo: (egoId: string) => boolean
  maxThreadspin: (egoId: string) => number | undefined
}

type EquippedEgo = NonNullable<SinnerEquipmentValue['egos'][keyof SinnerEquipmentValue['egos']]>

type SlotEgo = {
  id: string
  threadspin: number
  sinnerKey: string
  idPath: ContentPath
  threadspinPath: ContentPath
}

function slotEgo(egoType: string, ego: EquippedEgo, owner: SinnerEntry<unknown>): SlotEgo {
  const slotPath = extendPath(owner.path, field(EGOS), field(egoType))
  return {
    id: ego.id,
    threadspin: ego.threadspin,
    sinnerKey: owner.sinnerKey,
    idPath: extendPath(slotPath, field(ID)),
    threadspinPath: extendPath(slotPath, field(THREADSPIN)),
  }
}

function ceilingViolations(ego: SlotEgo, data: EgoReferenceData): EgoViolation[] {
  const ceiling = data.maxThreadspin(ego.id)
  const path = ego.threadspinPath
  if (ceiling === undefined) return [{ code: 'MISSING_EGO_THREADSPIN_CEILING', path }]
  return ego.threadspin > ceiling ? [{ code: 'OUT_OF_RANGE_EGO_THREADSPIN', path }] : []
}

function threadspinViolations(ego: SlotEgo, data: EgoReferenceData): EgoViolation[] {
  const bounded = outOfRangeViolations(
    ego.threadspin,
    ego.threadspinPath,
    THREADSPIN_BOUNDS,
    'OUT_OF_RANGE_EGO_THREADSPIN',
  )
  return bounded.length > 0 ? bounded : ceilingViolations(ego, data)
}

function slotViolations(ego: SlotEgo, data: EgoReferenceData): EgoViolation[] {
  if (!data.hasEgo(ego.id)) {
    return [{ code: 'UNKNOWN_EGO', path: ego.idPath, params: { id: ego.id } }]
  }
  if (!belongsToSinner(ego.id, ego.sinnerKey)) return [{ code: 'MISMATCHED_EGO', path: ego.idPath }]
  return threadspinViolations(ego, data)
}

export const egoRuleViolations = (
  entry: SinnerEntry<SinnerEquipmentValue>,
  data: EgoReferenceData,
): EgoViolation[] =>
  Object.entries(entry.value.egos)
    .flatMap(([egoType, ego]) => (ego === undefined ? [] : [slotEgo(egoType, ego, entry)]))
    .flatMap((ego) => slotViolations(ego, data))
    .sort(byPathThenCode)
