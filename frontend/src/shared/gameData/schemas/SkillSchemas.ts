import { z } from 'zod'

export const SkillDescEntrySchema = z.object({
  desc: z.string().optional(),
  coinDescs: z.array(z.string()).optional(),
})

export type SkillDescEntry = z.infer<typeof SkillDescEntrySchema>

export type Uptie = 1 | 2 | 3 | 4

export type Threadspin = 1 | 2 | 3 | 4 | 5
