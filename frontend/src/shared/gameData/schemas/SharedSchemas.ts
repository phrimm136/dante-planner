import { z } from 'zod'
import { AFFINITIES, EGO_TYPES } from '../constants'

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
 * Affinity type enum validation (data format names)
 * Used in spec list data files for internal computation
 */
export const AffinitySchema = z.enum(AFFINITIES)

/**
 * EGO type enum validation (ZAYIN, TETH, HE, WAW, ALEPH)
 * Ordered by ascending rank
 */
export const EgoTypeSchema = z.enum(EGO_TYPES)
