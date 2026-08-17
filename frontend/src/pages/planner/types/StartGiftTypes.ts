import type { z } from 'zod'
import type { StartEgoGiftPoolsSchema } from '../schemas/StartGiftSchemas'

// Start EGO Gift pool data from static/data/MD{version}/startEgoGiftPools.json
// Maps keyword to array of gift IDs
export type StartEgoGiftPools = z.infer<typeof StartEgoGiftPoolsSchema>
