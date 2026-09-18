import { z } from 'zod'

export const SkillTagSchema = z.record(z.string(), z.string())

export type SkillTags = z.infer<typeof SkillTagSchema>
