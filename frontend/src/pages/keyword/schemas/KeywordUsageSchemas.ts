import { z } from 'zod'

export const KeywordUsageSchema = z
  .object({
    identities: z.array(z.string()),
    enemy: z.array(z.string()),
    assistant: z.array(z.string()),
    sinnerEvent: z.array(z.string()),
  })
  .strict()

export const KeywordUsageListSchema = z.record(z.string(), KeywordUsageSchema)

export type KeywordUsage = z.infer<typeof KeywordUsageSchema>
