import { z } from 'zod'

export const EpithetListResponseSchema = z.object({
  epithets: z.array(z.string()),
})

export const UpdateUsernameEpithetRequestSchema = z.object({
  epithet: z.string().min(1, 'Epithet is required'),
})

export const UserDeletionResponseSchema = z
  .object({
    message: z.string(),
    deletedAt: z.string(),
    permanentDeleteAt: z.string().nullish(),
    gracePeriodDays: z.number(),
  })
  .strict()

export type EpithetListResponse = z.infer<typeof EpithetListResponseSchema>
export type UpdateUsernameEpithetRequest = z.infer<typeof UpdateUsernameEpithetRequestSchema>
export type UserDeletionResponse = z.infer<typeof UserDeletionResponseSchema>
