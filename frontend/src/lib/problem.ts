import { z } from 'zod'

/** Shape every backend error body is read through; every field is best-effort. */
export const ProblemSchema = z.object({
  detail: z.string().optional(),
  code: z.string().optional(),
  serverVersion: z.number().nullable().optional(),
})

export type Problem = z.infer<typeof ProblemSchema>
