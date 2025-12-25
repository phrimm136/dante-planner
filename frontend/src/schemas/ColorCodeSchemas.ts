import { z } from 'zod'

/**
 * Color Code Schemas
 *
 * Zod schemas for runtime validation of color code mapping data.
 * Maps attribute types (e.g., "CRIMSON") to hex color codes (e.g., "#A0392B").
 */

// ColorCodeMap schema - maps string keys to hex color values
export const ColorCodeMapSchema = z.record(z.string(), z.string())
