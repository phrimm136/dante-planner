import identityLevelCapJson from '@static/data/identityLevelCap.json'
import { z } from 'zod'

export const IdentityLevelCapSchema = z
  .object({
    maxLevel: z.number().int().min(1),
  })
  .strict()

export const IDENTITY_LEVEL_CAP: number =
  IdentityLevelCapSchema.parse(identityLevelCapJson).maxLevel
