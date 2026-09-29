import { MAX_ENTITY_TIER, MAX_LEVEL, MIN_ENTITY_TIER, MIN_LEVEL } from '@/shared/gameData'
import { outOfRangeViolations } from '../boundary'
import type { Bounds } from '../boundary'
import type { Codes } from '../codes'
import { extendPath, field } from '../contentPath'
import type { ContentPath } from '../contentPath'
import type { SinnerEquipmentValue } from '../contentShape'
import type { SinnerEntry } from '../sinnerEntries'
import { belongsToSinner } from '../sinnerScopedIds'

const IDENTITY = 'identity'
const ID = 'id'
const LEVEL = 'level'
const UPTIE = 'uptie'

const LEVEL_BOUNDS: Bounds = { min: MIN_LEVEL, max: MAX_LEVEL }
const UPTIE_BOUNDS: Bounds = { min: MIN_ENTITY_TIER.identity, max: MAX_ENTITY_TIER.identity }

export type IdentityViolation =
  | {
      code: Codes<
        'MISMATCHED_IDENTITY' | 'OUT_OF_RANGE_IDENTITY_LEVEL' | 'OUT_OF_RANGE_IDENTITY_UPTIE'
      >
      path: ContentPath
    }
  | { code: Codes<'UNKNOWN_IDENTITY'>; path: ContentPath; params: { id: string } }

export type IdentityReferenceData = { hasIdentity: (identityId: string) => boolean }

type EquippedIdentity = SinnerEquipmentValue['identity']

const progressionViolations = (
  identity: EquippedIdentity,
  identityPath: ContentPath,
): IdentityViolation[] => [
  ...outOfRangeViolations(
    identity.level,
    extendPath(identityPath, field(LEVEL)),
    LEVEL_BOUNDS,
    'OUT_OF_RANGE_IDENTITY_LEVEL',
  ),
  ...outOfRangeViolations(
    identity.uptie,
    extendPath(identityPath, field(UPTIE)),
    UPTIE_BOUNDS,
    'OUT_OF_RANGE_IDENTITY_UPTIE',
  ),
]

export function identityRuleViolations(
  { sinnerKey, path, value }: SinnerEntry<SinnerEquipmentValue>,
  data: IdentityReferenceData,
): IdentityViolation[] {
  const { identity } = value
  const identityPath = extendPath(path, field(IDENTITY))
  const idPath = extendPath(identityPath, field(ID))
  if (!data.hasIdentity(identity.id)) {
    return [{ code: 'UNKNOWN_IDENTITY', path: idPath, params: { id: identity.id } }]
  }
  if (!belongsToSinner(identity.id, sinnerKey)) {
    return [{ code: 'MISMATCHED_IDENTITY', path: idPath }]
  }
  return progressionViolations(identity, identityPath)
}
