import { z } from 'zod'

/**
 * Shared Schemas
 *
 * Common zod schemas used across multiple entity types.
 * These schemas provide validation for shared game mechanics and data structures.
 *
 * MAINTENANCE: When shared TypeScript types change, regenerate schemas using
 * the shared source generation tooling to maintain synchronization.
 */

/**
 * Sin type enum validation (display names)
 * Seven sin types matching game mechanics
 */
export const SinSchema = z.enum([
  'Wrath',
  'Lust',
  'Sloth',
  'Gluttony',
  'Gloom',
  'Pride',
  'Envy',
])

/**
 * Affinity type enum validation (data format names)
 * Used in spec list data files for internal computation
 */
export const AffinitySchema = z.enum([
  'CRIMSON',
  'SCARLET',
  'AMBER',
  'SHAMROCK',
  'AZURE',
  'INDIGO',
  'VIOLET',
])

/**
 * PassiveI18n schema - shared between Identity and EGO
 * Used for passive skill internationalization data
 */
export const PassiveI18nSchema = z.object({
  name: z.string(),
  desc: z.string(),
}).strict()
