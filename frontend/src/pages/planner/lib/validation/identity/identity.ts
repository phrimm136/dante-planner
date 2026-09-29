import { MAX_ENTITY_TIER, MAX_LEVEL, MIN_ENTITY_TIER, MIN_LEVEL } from '@/shared/gameData'
import {
  boundedIntegerViolations,
  invalidFieldType,
  isAbsent,
  isMissingText,
  isRecord,
} from '../boundary'
import type { Bounds, InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { extendPath, field } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { belongsToSinner } from '../sinnerScopedIds'

const IDENTITY = 'identity'
const ID = 'id'
const LEVEL = 'level'
const UPTIE = 'uptie'

const LEVEL_BOUNDS: Bounds = { min: MIN_LEVEL, max: MAX_LEVEL }
const UPTIE_BOUNDS: Bounds = { min: MIN_ENTITY_TIER.identity, max: MAX_ENTITY_TIER.identity }

export type IdentityViolation =
  | InvalidFieldType
  | {
      code: Codes<
        | 'MISSING_IDENTITY'
        | 'MISMATCHED_IDENTITY'
        | 'OUT_OF_RANGE_IDENTITY_LEVEL'
        | 'OUT_OF_RANGE_IDENTITY_UPTIE'
      >
      path: ContentPath
    }
  | { code: Codes<'UNKNOWN_IDENTITY'>; path: ContentPath; params: { id: string } }

export type EquippedIdentityValue = {
  id: string
  sinnerKey: string
  path: ContentPath
  identity: Record<string, unknown>
  identityPath: ContentPath
}

export type ParsedIdentity = {
  identity?: EquippedIdentityValue
  violations: readonly IdentityViolation[]
}

export type IdentityReferenceData = { hasIdentity: (identityId: string) => boolean }

const missingIdentity = (path: ContentPath): ParsedIdentity => ({
  violations: [{ code: 'MISSING_IDENTITY', path }],
})

const rejectedIdentity = (path: ContentPath): ParsedIdentity => ({
  violations: [invalidFieldType(path)],
})

function parseIdentityId(
  identity: Record<string, unknown>,
  identityPath: ContentPath,
  sinnerKey: string,
): ParsedIdentity {
  const id = identity[ID]
  const path = extendPath(identityPath, field(ID))
  if (isMissingText(id)) return missingIdentity(path)
  return typeof id === 'string'
    ? { identity: { id, sinnerKey, path, identity, identityPath }, violations: [] }
    : rejectedIdentity(path)
}

export function parseIdentity(
  sinnerEquipment: Record<string, unknown>,
  sinnerPath: ContentPath,
  sinnerKey: string,
): ParsedIdentity {
  const identity = sinnerEquipment[IDENTITY]
  const path = extendPath(sinnerPath, field(IDENTITY))
  if (isRecord(identity)) return parseIdentityId(identity, path, sinnerKey)
  return isAbsent(identity) ? missingIdentity(path) : rejectedIdentity(path)
}

const progressionViolations = ({
  identity,
  identityPath,
}: EquippedIdentityValue): IdentityViolation[] => [
  ...boundedIntegerViolations(
    identity[LEVEL],
    extendPath(identityPath, field(LEVEL)),
    LEVEL_BOUNDS,
    'OUT_OF_RANGE_IDENTITY_LEVEL',
  ),
  ...boundedIntegerViolations(
    identity[UPTIE],
    extendPath(identityPath, field(UPTIE)),
    UPTIE_BOUNDS,
    'OUT_OF_RANGE_IDENTITY_UPTIE',
  ),
]

export function identityRuleViolations(
  { identity }: ParsedIdentity,
  data: IdentityReferenceData,
): IdentityViolation[] {
  if (identity === undefined) return []
  if (!data.hasIdentity(identity.id)) {
    return [{ code: 'UNKNOWN_IDENTITY', path: identity.path, params: { id: identity.id } }]
  }
  if (!belongsToSinner(identity.id, identity.sinnerKey)) {
    return [{ code: 'MISMATCHED_IDENTITY', path: identity.path }]
  }
  return progressionViolations(identity)
}
