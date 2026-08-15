/**
 * Branded entity-id primitives and the base string patterns wire forms compose from.
 *
 * Value-role ids — an id referenced as data, such as a recipe material or a skill
 * entry — are numbers in the source data, and each gets a brand so that passing a
 * gift id where a skill id belongs is a compile error. Key-role ids (record keys,
 * route params) stay plain `string`.
 */

import { z } from 'zod'

export const IdentityIdSchema = z.number().int().brand<'IdentityId'>()
export const EGOIdSchema = z.number().int().brand<'EGOId'>()
/** Base id, carrying no enhancement prefix. */
export const EGOGiftIdSchema = z.number().int().brand<'EGOGiftId'>()
export const PassiveIdSchema = z.number().int().brand<'PassiveId'>()
export const SkillIdSchema = z.number().int().brand<'SkillId'>()
export const ThemePackIdSchema = z.number().int().brand<'ThemePackId'>()
export const SeasonSchema = z.number().int().brand<'Season'>()

export type IdentityId = z.infer<typeof IdentityIdSchema>
export type EGOId = z.infer<typeof EGOIdSchema>
export type EGOGiftId = z.infer<typeof EGOGiftIdSchema>
export type PassiveId = z.infer<typeof PassiveIdSchema>
export type SkillId = z.infer<typeof SkillIdSchema>
export type ThemePackId = z.infer<typeof ThemePackIdSchema>
export type Season = z.infer<typeof SeasonSchema>

/**
 * Base string patterns, unanchored, so both the anchored wire schemas and the
 * encoded-selection decoder read the format from one place.
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
