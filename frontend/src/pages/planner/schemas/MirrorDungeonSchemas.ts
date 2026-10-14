import { z } from 'zod'
import type { MDVersion } from '@/shared/gameData'

const MirrorDungeonSeasonSchema = z
  .object({
    version: z.number().int().positive(),
    identityMaxLevel: z.number().int().min(1),
    nameLocalizeId: z.string(),
  })
  .strict()

export type MirrorDungeonSeason = z.infer<typeof MirrorDungeonSeasonSchema>

export function mirrorDungeonSeasonSchema(version: MDVersion) {
  return MirrorDungeonSeasonSchema.refine((season) => season.version === version, {
    message: `expected season ${String(version)}`,
    path: ['version'],
  })
}
