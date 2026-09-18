import { z } from 'zod'

export const UserSettingsResponseSchema = z
  .object({
    syncEnabled: z.boolean(),
    syncChoiceMade: z.boolean(),
    notifyComments: z.boolean(),
    notifyRecommendations: z.boolean(),
    notifyNewPublications: z.boolean(),
  })
  .strict()

export const UpdateUserSettingsRequestSchema = z
  .object({
    syncEnabled: z.boolean().optional(),
    notifyComments: z.boolean().optional(),
    notifyRecommendations: z.boolean().optional(),
    notifyNewPublications: z.boolean().optional(),
  })
  .strict()

export type UserSettingsResponse = z.infer<typeof UserSettingsResponseSchema>
export type UpdateUserSettingsRequest = z.infer<typeof UpdateUserSettingsRequestSchema>
