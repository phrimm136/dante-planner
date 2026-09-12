import { z } from 'zod'
import { HexColorSchema } from '@/lib/colorUtils'
import { ATTRIBUTE_COLOR_TYPES, PASSIVE_IMPORTANCE_LEVELS, SINNER_NAMES } from '../constants'

/**
 * Color Schemas
 *
 * Zod schemas for the color tables under static/data/color/.
 */

/**
 * The roles one attribute type is painted in; NEUTRAL and NONE lack a saturated variant,
 * NONE lacks a type color, and WHITE and BLACK lack a background
 */
export const AttributeColorRolesSchema = z
  .object({
    type: HexColorSchema.optional(),
    frame: HexColorSchema,
    highSaturation: HexColorSchema.optional(),
    glow: HexColorSchema,
    hdr: HexColorSchema,
    fontOutline: HexColorSchema,
    background: HexColorSchema.optional(),
  })
  .strict()

export type AttributeColorRoles = z.infer<typeof AttributeColorRolesSchema>

export const AttributeColorCodeSchema = z.record(
  z.enum(ATTRIBUTE_COLOR_TYPES),
  AttributeColorRolesSchema,
)

export const SinnerColorCodeSchema = z.record(z.enum(SINNER_NAMES), HexColorSchema)

/**
 * Keyed by season code as a string, plus `default`
 */
export const SeasonColorCodeSchema = z.record(z.string(), HexColorSchema)

const ImportanceColorRolesSchema = z
  .object({
    stripe: HexColorSchema,
    passiveName: HexColorSchema,
    passivePanel: HexColorSchema,
    passiveGlow: HexColorSchema,
  })
  .strict()

const ImportanceTiersSchema = z.record(
  z.enum(PASSIVE_IMPORTANCE_LEVELS),
  ImportanceColorRolesSchema,
)

export const ImportanceColorCodeSchema = z
  .object({
    player: ImportanceTiersSchema,
    enemy: ImportanceTiersSchema,
  })
  .strict()
