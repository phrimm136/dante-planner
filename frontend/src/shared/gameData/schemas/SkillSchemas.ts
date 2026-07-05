import { z } from 'zod'

/**
 * Base skill-description entry shared by every skill-bearing entity (identity,
 * ego, …). Entity schemas reuse or `.extend()` this so their inferred types
 * inherit the base shape.
 */
export const SkillDescEntrySchema = z.object({
  desc: z.string().optional(),
  coinDescs: z.array(z.string()).optional(),
})

export type SkillDescEntry = z.infer<typeof SkillDescEntrySchema>

/** Identity uptie level, 1–4. */
export type Uptie = 1 | 2 | 3 | 4

/** EGO threadspin level, 1–5. */
export type Threadspin = 1 | 2 | 3 | 4 | 5
