/**
 * Branded entity-id primitives.
 *
 * An entity id is a string, not a number: each digit group carries its own
 * meaning — an identity id is a sinner index followed by an identity index, a
 * passive id ends in a variant digit, a gift id may carry an enhancement
 * prefix. Arithmetic on such an id is meaningless and leading zeros are part of
 * the value, so the primitives below brand the string form and the brand makes
 * passing one entity's id where another's belongs a compile error.
 *
 * The generated data serializes every id as a string, so these gate rather than
 * coerce: a number arriving here is a data regression and should fail loudly.
 */

import { z } from 'zod'

/**
 * Base string patterns, unanchored, so both these schemas and the encoded
 * selection decoder read a format from one place.
 */

/** Sinner-scoped id: {type code: 1 digit}{sinner index: 01-12}{entity index: 2+ digits}. */
function sinnerScopedIdPattern(typeCode: string): string {
  return `${typeCode}(0[1-9]|1[0-2])\\d{2,}`
}

/** Identity id: type code 1. */
export const IDENTITY_ID_PATTERN = sinnerScopedIdPattern('1')

/** EGO id: type code 2. */
export const EGO_ID_PATTERN = sinnerScopedIdPattern('2')

/** Any sinner-scoped id: type code 1 (identity) or 2 (EGO). */
export const SINNER_SCOPED_ID_PATTERN = sinnerScopedIdPattern('[12]')

/** Gift base id, without an enhancement prefix. */
export const GIFT_ID_PATTERN = '9\\d{3}'

/** Enhancement prefix a gift id may carry in its encoded form. */
export const GIFT_ENHANCEMENT_PREFIX_PATTERN = '[12]?'

/** Theme pack id. */
export const THEME_PACK_ID_PATTERN = '\\d{4}'

/** Passive and skill ids namespace by their owning entity, so only length is fixed. */
export const ENTITY_MEMBER_ID_PATTERN = '\\d{6,}'

const EntityIdBrandSchema = z.string().brand<'EntityId'>()

/** Supertype of every entity id; never a raw string. */
export type EntityId = z.infer<typeof EntityIdBrandSchema>

/**
 * Entity ids whose digits 2-3 name the owning sinner: identities and EGOs.
 * The schema is the boundary validator for sites holding an id known to be
 * one of the two without knowing which.
 */
export const SinnerScopedIdSchema = z
  .string()
  .regex(
    new RegExp(`^${SINNER_SCOPED_ID_PATTERN}$`),
    'Sinner-scoped ID must match pattern {1|2}{01-12}{2+ digits}',
  )
  .brand<'EntityId'>()
  .brand<'SinnerScopedId'>()

export type SinnerScopedId = z.infer<typeof SinnerScopedIdSchema>

/**
 * Entity-id schema factory: every entity id carries the base 'EntityId' brand
 * plus its own, so EntityId is the supertype of all entity ids. EncodedGiftId
 * stays outside the hierarchy — the encoding is a selection, not an entity
 * reference.
 */
function entityIdSchema<B extends string>(pattern: string, message: string) {
  return z
    .string()
    .regex(new RegExp(`^${pattern}$`), message)
    .brand<'EntityId'>()
    .brand<B>()
}

/** Like entityIdSchema, for the ids that additionally carry a sinner index. */
function sinnerScopedIdSchema<B extends string>(pattern: string, message: string) {
  return z
    .string()
    .regex(new RegExp(`^${pattern}$`), message)
    .brand<'EntityId'>()
    .brand<'SinnerScopedId'>()
    .brand<B>()
}

export const IdentityIdSchema = sinnerScopedIdSchema<'IdentityId'>(
  IDENTITY_ID_PATTERN,
  'Identity ID must match pattern 1{01-12}{2+ digits}',
)

export const EGOIdSchema = sinnerScopedIdSchema<'EGOId'>(
  EGO_ID_PATTERN,
  'EGO ID must match pattern 2{01-12}{2+ digits}',
)

/** Base id, carrying no enhancement prefix. */
export const EGOGiftIdSchema = entityIdSchema<'EGOGiftId'>(
  GIFT_ID_PATTERN,
  'Gift ID must match pattern 9{3 digits}',
)

/**
 * A gift id as a planner stores it: the base id behind an optional enhancement
 * digit. Branded separately from EGOGiftId — the encoding is a selection, not
 * an entity reference, and the two must not cross without a decode.
 */
export const EncodedGiftIdSchema = z
  .string()
  .regex(
    new RegExp(`^${GIFT_ENHANCEMENT_PREFIX_PATTERN}${GIFT_ID_PATTERN}$`),
    'Gift ID must match pattern {1|2|empty}9{3 digits}',
  )
  .brand<'EncodedGiftId'>()

export const PassiveIdSchema = entityIdSchema<'PassiveId'>(
  ENTITY_MEMBER_ID_PATTERN,
  'Passive ID must be digits',
)

export const SkillIdSchema = entityIdSchema<'SkillId'>(
  ENTITY_MEMBER_ID_PATTERN,
  'Skill ID must be digits',
)

export const ThemePackIdSchema = entityIdSchema<'ThemePackId'>(
  THEME_PACK_ID_PATTERN,
  'Theme Pack Id must match pattern {4 digits}',
)

export type IdentityId = z.infer<typeof IdentityIdSchema>
export type EGOId = z.infer<typeof EGOIdSchema>
export type EGOGiftId = z.infer<typeof EGOGiftIdSchema>
export type EncodedGiftId = z.infer<typeof EncodedGiftIdSchema>
export type PassiveId = z.infer<typeof PassiveIdSchema>
export type SkillId = z.infer<typeof SkillIdSchema>
export type ThemePackId = z.infer<typeof ThemePackIdSchema>
