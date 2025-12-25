import { z } from 'zod'

/**
 * Start Gift Schemas
 *
 * Zod schemas for runtime validation of start gift pool data.
 * Maps keywords to arrays of gift IDs.
 */

// StartEgoGiftPools schema - maps keyword to array of gift IDs
export const StartEgoGiftPoolsSchema = z.record(z.string(), z.array(z.number()))
