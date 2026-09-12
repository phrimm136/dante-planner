import { z } from 'zod'
import { HexColorSchema } from '@/lib/colorUtils'

/**
 * Color Code Schemas
 *
 * Zod schemas for runtime validation of skill-description tints.
 * Maps keyword ids (e.g., "Critical", "Positive") to hex color codes.
 */

export const ColorCodeMapSchema = z.record(z.string(), HexColorSchema)

export type ColorCodeMap = z.infer<typeof ColorCodeMapSchema>
