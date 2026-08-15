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

/** Identity id: 1 + sinner index (01-12) + at least two more digits. */
export const IDENTITY_ID_PATTERN = '1(0[1-9]|1[0-2])\\d{2,}'

/** EGO id: 2 + sinner index (01-12) + at least two more digits. */
export const EGO_ID_PATTERN = '2(0[1-9]|1[0-2])\\d{2,}'

/** Gift base id, without an enhancement prefix. */
export const GIFT_ID_PATTERN = '9\\d{3}'

/** Enhancement prefix a gift id may carry in its encoded form. */
export const GIFT_ENHANCEMENT_PREFIX_PATTERN = '[12]?'

/** Theme pack id. */
export const THEME_PACK_ID_PATTERN = '\\d{4}'

/** Passive and skill ids namespace by their owning entity, so only length is fixed. */
export const ENTITY_MEMBER_ID_PATTERN = '\\d{6,}'

export const IdentityIdSchema = z
  .string()
  .regex(
    new RegExp(`^${IDENTITY_ID_PATTERN}$`),
    'Identity ID must match pattern 1{01-12}{2+ digits}',
  )
  .brand<'IdentityId'>()

export const EGOIdSchema = z
  .string()
  .regex(new RegExp(`^${EGO_ID_PATTERN}$`), 'EGO ID must match pattern 2{01-12}{2+ digits}')
  .brand<'EGOId'>()

/** Base id, carrying no enhancement prefix. */
export const EGOGiftIdSchema = z
  .string()
  .regex(new RegExp(`^${GIFT_ID_PATTERN}$`), 'Gift ID must match pattern 9{3 digits}')
  .brand<'EGOGiftId'>()

/**
 * A gift id as a planner stores it: the base id behind an optional enhancement
 * digit. Unbranded — the encoding is a selection, not an entity reference.
 */
export const EncodedGiftIdSchema = z
  .string()
  .regex(
    new RegExp(`^${GIFT_ENHANCEMENT_PREFIX_PATTERN}${GIFT_ID_PATTERN}$`),
    'Gift ID must match pattern {1|2|empty}9{3 digits}',
  )

export const PassiveIdSchema = z
  .string()
  .regex(new RegExp(`^${ENTITY_MEMBER_ID_PATTERN}$`), 'Passive ID must be digits')
  .brand<'PassiveId'>()

export const SkillIdSchema = z
  .string()
  .regex(new RegExp(`^${ENTITY_MEMBER_ID_PATTERN}$`), 'Skill ID must be digits')
  .brand<'SkillId'>()

export const ThemePackIdSchema = z
  .string()
  .regex(new RegExp(`^${THEME_PACK_ID_PATTERN}$`), 'Theme Pack Id must match pattern {4 digits}')
  .brand<'ThemePackId'>()

export type IdentityId = z.infer<typeof IdentityIdSchema>
export type EGOId = z.infer<typeof EGOIdSchema>
export type EGOGiftId = z.infer<typeof EGOGiftIdSchema>
export type PassiveId = z.infer<typeof PassiveIdSchema>
export type SkillId = z.infer<typeof SkillIdSchema>
export type ThemePackId = z.infer<typeof ThemePackIdSchema>
